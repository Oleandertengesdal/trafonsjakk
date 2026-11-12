package no.trafonsjakk.review;

import java.time.Instant;
import java.time.Year;
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
import no.trafonsjakk.award.AwardRepository;
import no.trafonsjakk.award.AwardService;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.rating.Glicko2;
import no.trafonsjakk.rating.RatingHistory;
import no.trafonsjakk.rating.RatingHistoryRepository;
import no.trafonsjakk.review.YearReviewCalculator.AwardKind;
import no.trafonsjakk.review.YearReviewCalculator.AwardRow;
import no.trafonsjakk.review.YearReviewCalculator.GameRow;
import no.trafonsjakk.review.YearReviewDtos.BestMonth;
import no.trafonsjakk.review.YearReviewDtos.BuzzGame;
import no.trafonsjakk.review.YearReviewDtos.GameRef;
import no.trafonsjakk.review.YearReviewDtos.GroupYear;
import no.trafonsjakk.review.YearReviewDtos.PlayerYear;
import no.trafonsjakk.social.GameCommentRepository;
import no.trafonsjakk.social.KudosRepository;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Årets sjakk": a calendar year (Norwegian time) of confirmed games, for one player or the whole group. */
@Service
public class YearReviewService {

    static final int EARLIEST = 2000;

    private final GameRepository games;
    private final RatingHistoryRepository history;
    private final UserRepository users;
    private final KudosRepository kudos;
    private final GameCommentRepository comments;
    private final AwardRepository awards;
    private final ZoneId zone;

    public YearReviewService(GameRepository games, RatingHistoryRepository history, UserRepository users,
                             KudosRepository kudos, GameCommentRepository comments, AwardRepository awards,
                             AwardService awardService) {
        this.games = games;
        this.history = history;
        this.users = users;
        this.kudos = kudos;
        this.comments = comments;
        this.awards = awards;
        this.zone = awardService.zone();
    }

    @Transactional(readOnly = true)
    public PlayerYear player(int year, String username) {
        check(year);
        User u = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found"));
        Data d = load(year);
        YearReviewCalculator.PlayerYear p = YearReviewCalculator.player(u.getId(), d.rows, d.awards);
        boolean complete = complete(year);
        if (p == null) {
            return new PlayerYear(year, complete, PlayerRef.of(u), 0, 0, 0, 0, 0, null, null, null, List.of(), null, null,
                    null, null, null, null, 0, null, 0, null, d.playerCount(), List.of());
        }
        Map<Long, User> people = people(d, p);
        Function<Long, PlayerRef> ref = id -> PlayerRef.of(people.get(id));
        return new PlayerYear(year, complete, PlayerRef.of(u), p.games(), p.wins(), p.draws(), p.losses(), p.points(),
                round(p.ratingStart()), round(p.ratingEnd()), round(p.ratingPeak()),
                p.ratingByMonth().stream().map(m -> new YearReviewDtos.MonthRating(m.month().toString(), round(m.rating()))).toList(),
                p.bestMonth() == null ? null : new BestMonth(p.bestMonth().month().toString(), p.bestMonth().games(),
                        p.bestMonth().wins(), p.bestMonth().draws(), p.bestMonth().losses(), p.bestMonth().overExpected(),
                        round(p.bestMonth().ratingChange())),
                upset(p.biggestUpset(), ref),
                p.rival() == null ? null : new YearReviewDtos.Rival(ref.apply(p.rival().opponentId()), p.rival().games(),
                        p.rival().wins(), p.rival().draws(), p.rival().losses()),
                streak(p.longestWinStreak(), ref),
                p.favoriteOpening() == null ? null : new YearReviewDtos.Opening(p.favoriteOpening().name(),
                        p.favoriteOpening().games(), p.favoriteOpening().scorePct()),
                weekday(p.busiestWeekday()), p.monthsActive(),
                buzz(p.mostKudos(), d), p.gamesWithMoves(), p.rankByGames(), p.players(),
                p.awards().stream().map(a -> new YearReviewDtos.YearAward(a.month().toString(), AwardRepository.Kind.valueOf(a.kind().name()))).toList());
    }

    @Transactional(readOnly = true)
    public GroupYear group(int year) {
        check(year);
        Data d = load(year);
        YearReviewCalculator.GroupYear g = YearReviewCalculator.group(d.rows, d.awards);
        Set<Long> ids = new HashSet<>();
        g.mostGames().forEach(x -> ids.add(x.userId()));
        g.climbers().forEach(x -> ids.add(x.userId()));
        g.awardLeaders().forEach(x -> ids.add(x.userId()));
        if (g.giantKiller() != null) {
            ids.add(g.giantKiller().winnerId());
            ids.add(g.giantKiller().loserId());
        }
        if (g.longestWinStreak() != null) {
            ids.add(g.longestWinStreak().userId());
        }
        if (g.topRivalry() != null) {
            ids.add(g.topRivalry().aId());
            ids.add(g.topRivalry().bId());
        }
        Map<Long, User> people = usersById(ids);
        Function<Long, PlayerRef> ref = id -> PlayerRef.of(people.get(id));
        return new GroupYear(year, complete(year), g.games(), g.players(), g.whiteWins(), g.draws(), g.blackWins(),
                g.gamesWithMoves(), g.kudos(), g.comments(),
                g.busiestMonth() == null ? null : new YearReviewDtos.MonthCount(g.busiestMonth().month().toString(), g.busiestMonth().games()),
                weekday(g.busiestWeekday()), buzz(g.gameOfTheYear(), d), upset(g.giantKiller(), ref),
                g.awardLeaders().stream().map(a -> new YearReviewDtos.AwardLeader(ref.apply(a.userId()), a.playerOfMonth(), a.mostActive())).toList(),
                g.mostGames().stream().map(c -> new YearReviewDtos.PlayerCount(ref.apply(c.userId()), c.count())).toList(),
                g.climbers().stream().map(c -> new YearReviewDtos.Climber(ref.apply(c.userId()), round(c.change()), round(c.start()), round(c.end()))).toList(),
                streak(g.longestWinStreak(), ref),
                g.topRivalry() == null ? null : new YearReviewDtos.Rivalry(ref.apply(g.topRivalry().aId()), ref.apply(g.topRivalry().bId()),
                        g.topRivalry().games(), g.topRivalry().aWins(), g.topRivalry().draws(), g.topRivalry().bWins()));
    }

    /** The year's games with ratings, odds, kudos and comments, plus the awards decided in it. */
    private Data load(int year) {
        Instant from = Year.of(year).atDay(1).atStartOfDay(zone).toInstant();
        Instant to = Year.of(year + 1).atDay(1).atStartOfDay(zone).toInstant();
        List<Game> played = games.findConfirmedPlayedBetween(from, to);
        List<Long> ids = played.stream().map(Game::getId).toList();
        Map<Long, Map<Long, RatingHistory>> byGame = new HashMap<>();
        if (!ids.isEmpty()) {
            for (RatingHistory h : history.findByGameIdIn(ids)) {
                byGame.computeIfAbsent(h.getGameId(), k -> new HashMap<>()).put(h.getUserId(), h);
            }
        }
        Map<Long, Integer> kudosByGame = kudos.counts(ids);
        Map<Long, Integer> commentsByGame = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : comments.countByGames(ids)) {
                commentsByGame.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        List<GameRow> rows = new ArrayList<>(played.size());
        Map<Long, Game> gameById = new HashMap<>();
        for (Game g : played) {
            gameById.put(g.getId(), g);
            long w = g.getWhite().getId();
            long b = g.getBlack().getId();
            RatingHistory hw = byGame.getOrDefault(g.getId(), Map.of()).get(w);
            RatingHistory hb = byGame.getOrDefault(g.getId(), Map.of()).get(b);
            double wb = hw == null ? g.getWhite().getRating() : hw.getRatingBefore();
            double wa = hw == null ? wb : hw.getRatingAfter();
            double bb = hb == null ? g.getBlack().getRating() : hb.getRatingBefore();
            double ba = hb == null ? bb : hb.getRatingAfter();
            double expected = hw == null || hb == null ? 0.5 : Glicko2.expectedScore(
                    new Glicko2.Rating(hw.getRatingBefore(), hw.getRdBefore(), Glicko2.DEFAULT_VOLATILITY),
                    new Glicko2.Rating(hb.getRatingBefore(), hb.getRdBefore(), Glicko2.DEFAULT_VOLATILITY));
            rows.add(new GameRow(g.getId(), g.getPlayedAt(), g.getPlayedAt().atZone(zone).toLocalDate(), w, b,
                    g.getResult().whiteScore(), g.getOpening(), wb, wa, bb, ba, expected,
                    kudosByGame.getOrDefault(g.getId(), 0), commentsByGame.getOrDefault(g.getId(), 0),
                    g.getMoves() != null && !g.getMoves().isBlank()));
        }
        List<AwardRow> yearAwards = awards.won(null).stream()
                .filter(a -> a.month().getYear() == year)
                .map(a -> new AwardRow(a.month(), AwardKind.valueOf(a.kind().name()), a.userId()))
                .toList();
        return new Data(rows, gameById, yearAwards);
    }

    private record Data(List<GameRow> rows, Map<Long, Game> games, List<AwardRow> awards) {
        int playerCount() {
            Set<Long> ids = new HashSet<>();
            rows.forEach(r -> {
                ids.add(r.whiteId());
                ids.add(r.blackId());
            });
            return ids.size();
        }
    }

    private void check(int year) {
        if (year < EARLIEST || year > Year.now(zone).getValue()) {
            throw ApiException.badRequest("year.bad_year", "Year must be from 2000 up to this year");
        }
    }

    private boolean complete(int year) {
        return YearMonth.now(zone).getYear() > year;
    }

    private Map<Long, User> people(Data d, YearReviewCalculator.PlayerYear p) {
        Set<Long> ids = new HashSet<>();
        if (p.biggestUpset() != null) {
            ids.add(p.biggestUpset().winnerId());
            ids.add(p.biggestUpset().loserId());
        }
        if (p.rival() != null) {
            ids.add(p.rival().opponentId());
        }
        if (p.longestWinStreak() != null) {
            ids.add(p.longestWinStreak().userId());
        }
        return usersById(ids);
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static YearReviewDtos.Upset upset(YearReviewCalculator.Upset u, Function<Long, PlayerRef> ref) {
        return u == null ? null : new YearReviewDtos.Upset(u.gameId(), u.playedAt(), ref.apply(u.winnerId()),
                ref.apply(u.loserId()), u.expected(), round(u.winnerBefore()), round(u.loserBefore()));
    }

    private static YearReviewDtos.Streak streak(YearReviewCalculator.Streak s, Function<Long, PlayerRef> ref) {
        return s == null ? null : new YearReviewDtos.Streak(ref.apply(s.userId()), s.length(), s.from(), s.to());
    }

    private static YearReviewDtos.Weekday weekday(YearReviewCalculator.Weekday w) {
        return w == null ? null : new YearReviewDtos.Weekday(w.day(), w.games());
    }

    private static BuzzGame buzz(YearReviewCalculator.KudosGame k, Data d) {
        if (k == null) {
            return null;
        }
        Game g = d.games().get(k.gameId());
        return new BuzzGame(new GameRef(g.getId(), PlayerRef.of(g.getWhite()), PlayerRef.of(g.getBlack()), g.getResult(),
                g.getPlayedAt()), k.kudos(), k.comments());
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }
}
