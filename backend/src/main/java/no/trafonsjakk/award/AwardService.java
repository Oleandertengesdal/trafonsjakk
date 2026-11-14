package no.trafonsjakk.award;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.trafonsjakk.award.AwardRepository.Kind;
import no.trafonsjakk.award.MonthlyAwardCalculator.GameRow;
import no.trafonsjakk.award.MonthlyAwardCalculator.Standing;
import no.trafonsjakk.award.MonthlyAwardCalculator.Status;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.rating.Glicko2;
import no.trafonsjakk.rating.RatingHistory;
import no.trafonsjakk.rating.RatingHistoryRepository;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "Månedens spiller". The current month is a live race; a month is decided {@code app.awards.decide-after}
 * after it ends (so results confirmed late still count) and the winners are stored for good.
 * Deciding runs hourly, on startup and before every awards read (free hosting sleeps), and is idempotent.
 */
@Service
public class AwardService {

    private static final Logger log = LoggerFactory.getLogger(AwardService.class);
    /** Months before this can't hold games (see GameService). */
    static final YearMonth EARLIEST = YearMonth.of(2000, 1);
    static final int STANDINGS = 20;

    public record Award(String month, Kind kind, PlayerRef player, int games, double points, double expected,
                        double overExpected, int ratingChange, Instant decidedAt) {
    }

    public record StandingDto(PlayerRef player, int games, int wins, int draws, int losses, double points,
                              double expected, double overExpected, int ratingChange, boolean qualified) {
    }

    /** {@code winner} is only set once the month is decided (and someone qualified). */
    public record Category(Award winner, List<StandingDto> standings) {
    }

    public record Race(String month, Status status, Instant endsAt, Instant decidesAt, int minGames,
                       Category playerOfMonth, Category mostActive) {
    }

    private final GameRepository games;
    private final RatingHistoryRepository history;
    private final UserRepository users;
    private final AwardRepository awards;
    private final ZoneId zone;
    private final Duration decideAfter;
    private final int minGames;
    private final TransactionTemplate tx;

    public AwardService(GameRepository games, RatingHistoryRepository history, UserRepository users,
                        AwardRepository awards, AppProperties props, PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.games = games;
        this.history = history;
        this.users = users;
        this.awards = awards;
        this.zone = ZoneId.of(props.awards().zone());
        this.decideAfter = props.awards().decideAfter();
        this.minGames = props.awards().minGames();
    }

    public ZoneId zone() {
        return zone;
    }

    @Scheduled(cron = "0 7 * * * *")
    public void scheduled() {
        safely();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        safely();
    }

    private void safely() {
        try {
            // Through the template: a call to the @Transactional method on `this` would run without a
            // transaction, and a month could be left with only one of its two awards stored.
            tx.executeWithoutResult(status -> decideDueMonths(Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Deciding monthly awards failed", e);
        }
    }

    /** Stores the winners of every month that is over (plus the grace period) and not yet decided. */
    @Transactional
    public void decideDueMonths(Instant now) {
        Instant first = games.firstConfirmedPlayedAt();
        if (first == null) {
            return;
        }
        YearMonth from = YearMonth.from(first.atZone(zone));
        YearMonth to = YearMonth.from(now.atZone(zone));
        while (!to.isBefore(from) && MonthlyAwardCalculator.status(to, now, zone, decideAfter) != Status.DECIDED) {
            to = to.minusMonths(1);
        }
        if (to.isBefore(from)) {
            return;
        }
        Set<YearMonth> decided = awards.decidedMonths();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            if (!decided.contains(m)) {
                decide(m, now);
            }
        }
    }

    private void decide(YearMonth month, Instant now) {
        MonthlyAwardCalculator.Result r = MonthlyAwardCalculator.compute(rows(month), minGames);
        store(month, Kind.PLAYER_OF_MONTH, r.playerOfMonthWinner(), r.playerOfMonth(), now);
        store(month, Kind.MOST_ACTIVE, r.mostActiveWinner(), r.mostActive(), now);
        log.info("Decided awards for {}: player of the month {}, most active {}", month, r.playerOfMonthWinner(), r.mostActiveWinner());
    }

    private void store(YearMonth month, Kind kind, Long winner, List<Standing> standings, Instant now) {
        Standing s = winner == null ? null : standings.stream().filter(x -> x.userId() == winner).findFirst().orElseThrow();
        if (s == null) {
            awards.insert(month, kind, null, 0, 0, 0, 0, now);
        } else {
            awards.insert(month, kind, s.userId(), s.games(), s.points(), s.expected(), s.ratingChange(), now);
        }
    }

    /** The month's standings as they are now, and its winners if it's decided. Defaults to the current month. */
    @Transactional(readOnly = true)
    public Race race(String monthParam) {
        Instant now = Instant.now();
        YearMonth current = YearMonth.from(now.atZone(zone));
        YearMonth month = monthParam == null || monthParam.isBlank() ? current : parse(monthParam, current);

        MonthlyAwardCalculator.Result r = MonthlyAwardCalculator.compute(rows(month), minGames);
        Map<Long, User> people = usersById(r.playerOfMonth().stream().map(Standing::userId).toList());
        Map<Kind, Award> winners = new HashMap<>();
        Status status = MonthlyAwardCalculator.status(month, now, zone, decideAfter);
        if (status == Status.DECIDED) {
            List<AwardRepository.Row> rows = awards.forMonth(month);
            Map<Long, User> winnerUsers = usersById(rows.stream().map(AwardRepository.Row::userId).filter(id -> id != null).toList());
            for (AwardRepository.Row row : rows) {
                if (row.userId() != null) {
                    winners.put(row.kind(), toAward(row, winnerUsers));
                }
            }
        }
        Instant ends = MonthlyAwardCalculator.endsAt(month, zone);
        return new Race(month.toString(), status, ends, ends.plus(decideAfter), minGames,
                new Category(winners.get(Kind.PLAYER_OF_MONTH), standings(r.playerOfMonth(), people)),
                new Category(winners.get(Kind.MOST_ACTIVE), standings(r.mostActive(), people)));
    }

    /** Awards that were won, newest first; only one person's when {@code username} is given. */
    @Transactional(readOnly = true)
    public List<Award> won(String username) {
        Long userId = null;
        if (username != null && !username.isBlank()) {
            userId = users.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found")).getId();
        }
        List<AwardRepository.Row> rows = awards.won(userId);
        Map<Long, User> people = usersById(rows.stream().map(AwardRepository.Row::userId).toList());
        return rows.stream().map(row -> toAward(row, people)).toList();
    }

    private static YearMonth parse(String value, YearMonth current) {
        YearMonth m;
        try {
            if (!value.matches("\\d{4}-\\d{2}")) {
                throw new IllegalArgumentException();
            }
            m = YearMonth.parse(value);
        } catch (RuntimeException e) {
            throw badMonth();
        }
        if (m.isAfter(current) || m.isBefore(EARLIEST)) {
            throw badMonth();
        }
        return m;
    }

    private static ApiException badMonth() {
        return ApiException.badRequest("award.bad_month", "Month must be YYYY-MM, from 2000 up to this month");
    }

    /** The month's confirmed games, with expected scores from the ratings each side had before the game. */
    private List<GameRow> rows(YearMonth month) {
        List<Game> played = games.findConfirmedPlayedBetween(MonthlyAwardCalculator.startsAt(month, zone),
                MonthlyAwardCalculator.endsAt(month, zone));
        if (played.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, RatingHistory>> byGame = new HashMap<>();
        for (RatingHistory h : history.findByGameIdIn(played.stream().map(Game::getId).toList())) {
            byGame.computeIfAbsent(h.getGameId(), k -> new HashMap<>()).put(h.getUserId(), h);
        }
        List<GameRow> rows = new ArrayList<>(played.size());
        for (Game g : played) {
            Map<Long, RatingHistory> h = byGame.getOrDefault(g.getId(), Map.of());
            RatingHistory w = h.get(g.getWhite().getId());
            RatingHistory b = h.get(g.getBlack().getId());
            double expected = 0.5;
            double wChange = 0;
            double bChange = 0;
            if (w != null && b != null) {
                expected = Glicko2.expectedScore(
                        new Glicko2.Rating(w.getRatingBefore(), w.getRdBefore(), Glicko2.DEFAULT_VOLATILITY),
                        new Glicko2.Rating(b.getRatingBefore(), b.getRdBefore(), Glicko2.DEFAULT_VOLATILITY));
                wChange = w.getRatingAfter() - w.getRatingBefore();
                bChange = b.getRatingAfter() - b.getRatingBefore();
            }
            rows.add(new GameRow(g.getWhite().getId(), g.getBlack().getId(), g.getResult().whiteScore(), expected, wChange, bChange));
        }
        return rows;
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(unique).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static List<StandingDto> standings(List<Standing> list, Map<Long, User> people) {
        return list.stream().limit(STANDINGS)
                .map(s -> new StandingDto(PlayerRef.of(people.get(s.userId())), s.games(), s.wins(), s.draws(), s.losses(),
                        s.points(), s.expected(), s.overExpected(), (int) Math.round(s.ratingChange()), s.qualified()))
                .toList();
    }

    private static Award toAward(AwardRepository.Row row, Map<Long, User> people) {
        return new Award(row.month().toString(), row.kind(), PlayerRef.of(people.get(row.userId())), row.games(),
                row.points(), row.expected(), row.points() - row.expected(), (int) Math.round(row.ratingChange()), row.decidedAt());
    }
}
