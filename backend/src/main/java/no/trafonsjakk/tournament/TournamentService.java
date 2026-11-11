package no.trafonsjakk.tournament;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.game.GameStatus;
import no.trafonsjakk.tournament.TournamentDtos.CreateTournamentRequest;
import no.trafonsjakk.tournament.TournamentDtos.MatchDto;
import no.trafonsjakk.tournament.TournamentDtos.MatchGameDto;
import no.trafonsjakk.tournament.TournamentDtos.MyMatchDto;
import no.trafonsjakk.tournament.TournamentDtos.ParticipantDto;
import no.trafonsjakk.tournament.TournamentDtos.RoundDto;
import no.trafonsjakk.tournament.TournamentDtos.StandingDto;
import no.trafonsjakk.tournament.TournamentDtos.TournamentDetail;
import no.trafonsjakk.tournament.TournamentDtos.TournamentSummary;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tournament lifecycle and the event-driven bracket/standings engine.
 * See docs/ARCHITECTURE.md §3.5.
 */
@Service
public class TournamentService {

    static final int MAX_PLAYERS = 32;

    private final TournamentRepository tournaments;
    private final TournamentParticipantRepository participants;
    private final TournamentMatchRepository matches;
    private final GameRepository games;
    private final UserRepository users;

    public TournamentService(TournamentRepository tournaments, TournamentParticipantRepository participants,
                             TournamentMatchRepository matches, GameRepository games, UserRepository users) {
        this.tournaments = tournaments;
        this.participants = participants;
        this.matches = matches;
        this.games = games;
        this.users = users;
    }

    // ---- queries -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TournamentSummary> list() {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : participants.countPerTournament()) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return tournaments.findAllByOrderByCreatedAtDesc().stream()
                .map(t -> summary(t, counts.getOrDefault(t.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentDetail get(long id, CurrentUser actor) {
        return detail(load(id), actor);
    }

    @Transactional(readOnly = true)
    public List<MyMatchDto> myMatches(CurrentUser actor) {
        List<MyMatchDto> out = new ArrayList<>();
        Map<Long, Integer> totalRounds = new HashMap<>();
        for (TournamentMatch m : matches.findReadyForUser(actor.id())) {
            if (games.existsOpenForMatch(m.getId())) {
                continue;
            }
            Tournament t = m.getTournament();
            int total = totalRounds.computeIfAbsent(t.getId(), matches::maxRound);
            List<Game> played = games.findByMatchId(m.getId());
            boolean replay = needsReplay(t, m, played);
            // A drawn knockout game is replayed with the colours of the last game reversed
            User white = m.getWhite();
            User black = m.getBlack();
            if (replay) {
                Game last = played.stream().filter(g -> g.getStatus() == GameStatus.CONFIRMED).reduce((a, b) -> b)
                        .orElseThrow();
                white = last.getBlack();
                black = last.getWhite();
            }
            out.add(new MyMatchDto(m.getId(), t.getId(), t.getName(), t.getFormat(), m.getRoundNumber(), total,
                    roundName(t, m.getRoundNumber(), total), PlayerRef.of(white), PlayerRef.of(black), replay));
        }
        return out;
    }

    // ---- registration --------------------------------------------------------------------------

    @Transactional
    public TournamentDetail create(CreateTournamentRequest req, CurrentUser actor) {
        String name = req.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("tournament.name_required", "Give the tournament a name");
        }
        boolean doubleRr = req.format() == TournamentFormat.ROUND_ROBIN && req.isDouble();
        User creator = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
        Tournament t = tournaments.save(new Tournament(name, req.format(), doubleRr, creator));
        for (User u : validPlayers(req.participantIds())) {
            participants.save(new TournamentParticipant(t, u));
        }
        return detail(t, actor);
    }

    @Transactional
    public TournamentDetail setParticipants(long id, List<Long> ids, CurrentUser actor) {
        Tournament t = loadForUpdate(id);
        requireManage(t, actor);
        requireStatus(t, TournamentStatus.REGISTRATION);
        participants.deleteByTournamentId(id);
        participants.flush();
        for (User u : validPlayers(ids)) {
            participants.save(new TournamentParticipant(t, u));
        }
        return detail(t, actor);
    }

    @Transactional
    public TournamentDetail join(long id, CurrentUser actor) {
        Tournament t = loadForUpdate(id);
        requireStatus(t, TournamentStatus.REGISTRATION);
        List<TournamentParticipant> ps = participants.findByTournamentId(id);
        if (ps.stream().anyMatch(p -> p.getUser().getId() == actor.id())) {
            throw ApiException.conflict("tournament.already_joined", "You're already in this tournament");
        }
        if (ps.size() >= MAX_PLAYERS) {
            throw ApiException.conflict("tournament.full", "This tournament is full");
        }
        User me = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
        participants.save(new TournamentParticipant(t, me));
        return detail(t, actor);
    }

    @Transactional
    public TournamentDetail leave(long id, CurrentUser actor) {
        Tournament t = loadForUpdate(id);
        requireStatus(t, TournamentStatus.REGISTRATION);
        TournamentParticipant.Key key = new TournamentParticipant.Key(id, actor.id());
        if (!participants.existsById(key)) {
            throw ApiException.conflict("tournament.not_joined", "You're not in this tournament");
        }
        participants.deleteById(key);
        participants.flush();
        return detail(t, actor);
    }

    @Transactional
    public TournamentDetail cancel(long id, CurrentUser actor) {
        Tournament t = loadForUpdate(id);
        requireManage(t, actor);
        if (t.getStatus() != TournamentStatus.REGISTRATION && t.getStatus() != TournamentStatus.IN_PROGRESS) {
            throw ApiException.conflict("tournament.finished", "This tournament has already finished");
        }
        t.cancel(Instant.now());
        return detail(t, actor);
    }

    // ---- start: seeding + fixture generation ---------------------------------------------------

    @Transactional
    public TournamentDetail start(long id, CurrentUser actor) {
        Tournament t = loadForUpdate(id);
        requireManage(t, actor);
        requireStatus(t, TournamentStatus.REGISTRATION);

        List<TournamentParticipant> ps = new ArrayList<>(participants.findByTournamentId(id));
        int n = ps.size();
        int min = t.getFormat() == TournamentFormat.ROUND_ROBIN ? 3 : 2;
        if (n < min) {
            throw ApiException.conflict("tournament.min_players", "Need at least " + min + " players to start").with("min", min);
        }

        ps.sort(Comparator.comparingDouble((TournamentParticipant p) -> -p.getUser().getRating())
                .thenComparing(p -> p.getUser().getUsername().toLowerCase(Locale.ROOT)));
        Map<Long, User> byId = new HashMap<>();
        List<Long> seeded = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            TournamentParticipant p = ps.get(i);
            p.setSeed(i + 1);
            byId.put(p.getUser().getId(), p.getUser());
            seeded.add(p.getUser().getId());
        }

        if (t.getFormat() == TournamentFormat.ROUND_ROBIN) {
            for (RoundRobinScheduler.Pairing p : RoundRobinScheduler.schedule(seeded, t.isDoubleRoundRobin())) {
                matches.save(new TournamentMatch(t, p.round(), p.board(), byId.get(p.white()), byId.get(p.black()),
                        MatchStatus.READY));
            }
        } else {
            generateBracket(t, seeded, byId);
        }
        t.start(Instant.now());
        return detail(t, actor);
    }

    private void generateBracket(Tournament t, List<Long> seeded, Map<Long, User> byId) {
        int n = seeded.size();
        int rounds = KnockoutBracket.roundCount(n);
        Map<String, TournamentMatch> grid = new HashMap<>();

        for (KnockoutBracket.Slot s : KnockoutBracket.firstRound(seeded)) {
            TournamentMatch m;
            if (s.isBye()) {
                User solo = byId.get(s.soloPlayer());
                m = new TournamentMatch(t, 1, s.board(), solo, null, MatchStatus.BYE);
                m.setWinner(solo);
            } else {
                m = new TournamentMatch(t, 1, s.board(), byId.get(s.white()), byId.get(s.black()), MatchStatus.READY);
            }
            grid.put(key(1, s.board()), matches.save(m));
        }
        for (int r = 2; r <= rounds; r++) {
            for (int b = 0; b < KnockoutBracket.matchesInRound(n, r); b++) {
                grid.put(key(r, b), matches.save(new TournamentMatch(t, r, b, null, null, MatchStatus.WAITING)));
            }
        }
        // Byes advance immediately
        if (rounds > 1) {
            for (int b = 0; b < KnockoutBracket.matchesInRound(n, 1); b++) {
                TournamentMatch m = grid.get(key(1, b));
                if (m.getStatus() == MatchStatus.BYE) {
                    place(grid.get(key(2, KnockoutBracket.nextBoard(b))), m.getWinner(),
                            KnockoutBracket.advancesAsWhite(b));
                }
            }
        }
    }

    private static String key(int round, int board) {
        return round + ":" + board;
    }

    private static void place(TournamentMatch next, User player, boolean asWhite) {
        if (asWhite) {
            next.setWhite(player);
        } else {
            next.setBlack(player);
        }
        if (next.getWhite() != null && next.getBlack() != null) {
            next.setStatus(MatchStatus.READY);
        }
    }

    // ---- live updates --------------------------------------------------------------------------

    /**
     * Recomputes one match's outcome from its confirmed games and applies the difference to the
     * bracket/tournament. Called whenever a linked game is confirmed, corrected or voided.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reevaluateMatch(long matchId) {
        // Lock the tournament before reading any of its matches: results confirmed at the same moment
        // must not each see the other's match as unfinished (stuck standings) or race for a bracket slot.
        Long tournamentId = matches.findTournamentId(matchId).orElse(null);
        if (tournamentId == null) {
            return;
        }
        tournaments.findByIdForUpdate(tournamentId);
        TournamentMatch m = matches.findById(matchId).orElse(null);
        if (m == null || m.getStatus() == MatchStatus.BYE) {
            return;
        }
        Tournament t = m.getTournament();
        if (t.getStatus() != TournamentStatus.IN_PROGRESS && t.getStatus() != TournamentStatus.COMPLETED) {
            return;
        }
        List<Game> confirmed = games.findByMatchId(matchId).stream()
                .filter(g -> g.getStatus() == GameStatus.CONFIRMED)
                .toList();

        if (t.getFormat() == TournamentFormat.ROUND_ROBIN) {
            Game first = confirmed.isEmpty() ? null : confirmed.getFirst();
            m.setResult(first == null ? null : resultForMatch(first, m));
            m.setWinner(first == null ? null : first.winner());
            m.setStatus(first == null ? MatchStatus.READY : MatchStatus.COMPLETED);
            matches.flush();
            updateRoundRobinCompletion(t);
            return;
        }

        Game decisive = confirmed.stream().filter(g -> g.getResult().isDecisive()).findFirst().orElse(null);
        User newWinner = decisive == null ? null : decisive.winner();
        Long oldId = m.getWinner() == null ? null : m.getWinner().getId();
        Long newId = newWinner == null ? null : newWinner.getId();

        if (!Objects.equals(oldId, newId)) {
            int totalRounds = matches.maxRound(t.getId());
            if (m.getRoundNumber() == totalRounds) {
                if (newWinner != null) {
                    t.complete(newWinner, Instant.now());
                } else {
                    t.reopen();
                }
            } else {
                TournamentMatch next = matches.findSlot(t.getId(), m.getRoundNumber() + 1,
                                KnockoutBracket.nextBoard(m.getBoardNumber()))
                        .orElseThrow(() -> new IllegalStateException("Bracket slot missing"));
                boolean asWhite = KnockoutBracket.advancesAsWhite(m.getBoardNumber());
                if (oldId != null) {
                    if (next.getStatus() == MatchStatus.COMPLETED || games.existsActiveForMatch(next.getId())) {
                        throw ApiException.conflict("match.next_round_started", "The next round has already started. "
                                + "Void the games played after this one first.");
                    }
                    if (asWhite) {
                        next.setWhite(null);
                    } else {
                        next.setBlack(null);
                    }
                    next.setStatus(MatchStatus.WAITING);
                }
                if (newWinner != null) {
                    place(next, newWinner, asWhite);
                }
            }
        }
        m.setWinner(newWinner);
        m.setResult(decisive == null ? null : resultForMatch(decisive, m));
        m.setStatus(newWinner != null ? MatchStatus.COMPLETED : MatchStatus.READY);
    }

    private void updateRoundRobinCompletion(Tournament t) {
        List<TournamentMatch> all = matches.findByTournamentId(t.getId());
        boolean done = all.stream().allMatch(x -> x.getStatus() == MatchStatus.COMPLETED);
        if (done) {
            List<StandingsCalculator.Standing> table = standings(t, all);
            User champion = table.isEmpty() ? null : users.getReferenceById(table.getFirst().playerId());
            t.complete(champion, Instant.now());
        } else if (t.getStatus() == TournamentStatus.COMPLETED) {
            t.reopen();
        }
    }

    /** The match result oriented to the match's white/black (players may have swapped colours). */
    private static GameResult resultForMatch(Game g, TournamentMatch m) {
        if (m.getWhite() == null || g.getWhite().getId().equals(m.getWhite().getId())) {
            return g.getResult();
        }
        return switch (g.getResult()) {
            case WHITE_WIN -> GameResult.BLACK_WIN;
            case BLACK_WIN -> GameResult.WHITE_WIN;
            case DRAW -> GameResult.DRAW;
        };
    }

    // ---- mapping -------------------------------------------------------------------------------

    private TournamentDetail detail(Tournament t, CurrentUser actor) {
        List<TournamentParticipant> ps = new ArrayList<>(participants.findByTournamentId(t.getId()));
        ps.sort(Comparator.comparing((TournamentParticipant p) -> p.getSeed() == null ? Integer.MAX_VALUE : p.getSeed())
                .thenComparing(p -> p.getUser().getUsername().toLowerCase(Locale.ROOT)));
        List<TournamentMatch> ms = matches.findByTournamentId(t.getId());

        Map<Long, List<Game>> gamesByMatch = ms.isEmpty() ? Map.of()
                : games.findActiveByMatchIds(ms.stream().map(TournamentMatch::getId).toList()).stream()
                .collect(Collectors.groupingBy(g -> g.getMatch().getId()));

        int totalRounds = ms.stream().mapToInt(TournamentMatch::getRoundNumber).max().orElse(0);
        TreeMap<Integer, List<MatchDto>> byRound = new TreeMap<>();
        Integer currentRound = null;
        int completed = 0;
        int playable = 0;
        for (TournamentMatch m : ms) {
            List<Game> gs = gamesByMatch.getOrDefault(m.getId(), List.of());
            byRound.computeIfAbsent(m.getRoundNumber(), r -> new ArrayList<>()).add(matchDto(t, m, gs));
            if (m.getStatus() != MatchStatus.BYE) {
                playable++;
                if (m.getStatus() == MatchStatus.COMPLETED) {
                    completed++;
                }
            }
            if (m.getStatus() == MatchStatus.READY && (currentRound == null || m.getRoundNumber() < currentRound)) {
                currentRound = m.getRoundNumber();
            }
        }
        List<RoundDto> rounds = new ArrayList<>();
        byRound.forEach((r, list) -> rounds.add(new RoundDto(r, roundName(t, r, totalRounds), list)));

        List<StandingDto> standingDtos = null;
        if (t.getFormat() == TournamentFormat.ROUND_ROBIN && !ms.isEmpty()) {
            Map<Long, User> byId = ps.stream().map(TournamentParticipant::getUser)
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            standingDtos = standings(t, ms).stream()
                    .map(s -> new StandingDto(s.rank(), PlayerRef.of(byId.get(s.playerId())), s.played(), s.wins(),
                            s.draws(), s.losses(), s.points(), s.sonnebornBerger()))
                    .toList();
        }

        boolean joined = ps.stream().anyMatch(p -> p.getUser().getId() == actor.id());
        boolean open = t.getStatus() == TournamentStatus.REGISTRATION || t.getStatus() == TournamentStatus.IN_PROGRESS;
        boolean canManage = open && (actor.admin() || t.getCreatedBy().getId() == actor.id());
        boolean canJoin = t.getStatus() == TournamentStatus.REGISTRATION && !joined && ps.size() < MAX_PLAYERS;

        return new TournamentDetail(summary(t, ps.size()),
                ps.stream().map(p -> new ParticipantDto(PlayerRef.of(p.getUser()), p.getSeed())).toList(),
                rounds, standingDtos, currentRound, totalRounds, completed, playable, canManage, canJoin, joined);
    }

    private List<StandingsCalculator.Standing> standings(Tournament t, List<TournamentMatch> ms) {
        List<TournamentParticipant> ps = participants.findByTournamentId(t.getId());
        Map<Long, String> names = ps.stream().collect(Collectors.toMap(p -> p.getUser().getId(),
                p -> p.getUser().getUsername().toLowerCase(Locale.ROOT)));
        List<StandingsCalculator.Result> results = ms.stream()
                .filter(m -> m.getStatus() == MatchStatus.COMPLETED && m.getResult() != null)
                .map(m -> new StandingsCalculator.Result(m.getWhite().getId(), m.getBlack().getId(),
                        m.getResult().whiteScore()))
                .toList();
        return StandingsCalculator.compute(new ArrayList<>(names.keySet()), results,
                Comparator.comparing((Long id) -> names.getOrDefault(id, "")));
    }

    private MatchDto matchDto(Tournament t, TournamentMatch m, List<Game> gs) {
        Long openGameId = gs.stream()
                .filter(g -> g.getStatus() == GameStatus.PENDING || g.getStatus() == GameStatus.DISPUTED)
                .map(Game::getId).findFirst().orElse(null);
        List<MatchGameDto> gameDtos = gs.stream()
                .map(g -> new MatchGameDto(g.getId(), g.getWhite().getId(), g.getBlack().getId(), g.getResult(),
                        g.getStatus(), g.getPlayedAt()))
                .toList();
        return new MatchDto(m.getId(), m.getRoundNumber(), m.getBoardNumber(), PlayerRef.of(m.getWhite()),
                PlayerRef.of(m.getBlack()), m.getStatus(), m.getResult(), PlayerRef.of(m.getWinner()), gameDtos,
                openGameId, needsReplay(t, m, gs));
    }

    private static boolean needsReplay(Tournament t, TournamentMatch m, List<Game> gs) {
        return t.getFormat() == TournamentFormat.SINGLE_ELIMINATION && m.getStatus() == MatchStatus.READY
                && gs.stream().anyMatch(g -> g.getStatus() == GameStatus.CONFIRMED && !g.getResult().isDecisive());
    }

    private static String roundName(Tournament t, int round, int totalRounds) {
        return t.getFormat() == TournamentFormat.SINGLE_ELIMINATION
                ? KnockoutBracket.roundName(round, totalRounds)
                : "Round " + round;
    }

    private TournamentSummary summary(Tournament t, int participantCount) {
        return new TournamentSummary(t.getId(), t.getName(), t.getFormat(), t.getStatus(), t.isDoubleRoundRobin(),
                participantCount, PlayerRef.of(t.getCreatedBy()), PlayerRef.of(t.getWinner()), t.getCreatedAt(),
                t.getStartedAt(), t.getCompletedAt());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Tournament load(long id) {
        return tournaments.findById(id).orElseThrow(() -> ApiException.notFound("tournament.not_found", "Tournament not found"));
    }

    private Tournament loadForUpdate(long id) {
        return tournaments.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("tournament.not_found", "Tournament not found"));
    }

    private static void requireManage(Tournament t, CurrentUser actor) {
        if (!actor.admin() && t.getCreatedBy().getId() != actor.id()) {
            throw ApiException.forbidden("tournament.not_manager", "Only the organiser or an admin can do that");
        }
    }

    private static void requireStatus(Tournament t, TournamentStatus expected) {
        if (t.getStatus() != expected) {
            throw ApiException.conflict("tournament.wrong_status", "Not possible while the tournament is "
                    + t.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ')).with("status", t.getStatus().name());
        }
    }

    private List<User> validPlayers(List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw ApiException.badRequest("tournament.duplicate_player", "A player is listed twice");
        }
        if (unique.size() > MAX_PLAYERS) {
            throw ApiException.badRequest("tournament.max_players", "At most " + MAX_PLAYERS + " players").with("max", MAX_PLAYERS);
        }
        List<User> found = users.findAllById(unique);
        if (found.size() != unique.size() || found.stream().anyMatch(u -> !u.isEnabled())) {
            throw ApiException.badRequest("player.unknown_or_disabled", "Unknown or disabled player");
        }
        return found;
    }
}
