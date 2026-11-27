package no.trafonsjakk.online;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import no.trafonsjakk.chess.ChessGame;
import no.trafonsjakk.chess.Color;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.GameDtos.RatingChange;
import no.trafonsjakk.game.GameDtos.RatingDelta;
import no.trafonsjakk.game.GameMode;
import no.trafonsjakk.game.GameService;
import no.trafonsjakk.online.OnlineDtos.ChallengeDto;
import no.trafonsjakk.online.OnlineDtos.ChallengeRequest;
import no.trafonsjakk.online.OnlineDtos.ClockDto;
import no.trafonsjakk.online.OnlineDtos.Lobby;
import no.trafonsjakk.online.OnlineDtos.MoveRequest;
import no.trafonsjakk.online.OnlineDtos.OnlineGameDto;
import no.trafonsjakk.online.OnlineDtos.TimeControlDto;
import no.trafonsjakk.push.PushEvents.ChallengeReceived;
import no.trafonsjakk.push.PushEvents.YourMove;
import no.trafonsjakk.rating.RatingHistory;
import no.trafonsjakk.rating.RatingHistoryRepository;
import no.trafonsjakk.rating.RatingService;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Online games, with the server as arbiter: it checks every move against the rules ({@link ChessGame}),
 * keeps the clock ({@link Clock}) and ends the game when the board, the clock or the players do.
 * <p>
 * The free API has no WebSockets (and sleeps when idle), so the pages poll. Nothing ticks on the server:
 * a game whose time ran out is ended the next time anyone looks at it, makes a move, opens the lobby,
 * or when the sweep below runs. A rated game with a result then becomes an ordinary, confirmed game
 * ({@code mode = ONLINE}) and moves the online rating.
 * <p>
 * Each command runs in its own transaction with the game row locked. A move that turns out to be too
 * late still ends the game on time: that part is committed before the move is refused.
 */
@Service
public class OnlineGameService {

    static final Duration LIVE_CHALLENGE_LIFETIME = Duration.ofHours(1);
    static final Duration DAILY_CHALLENGE_LIFETIME = Duration.ofDays(7);
    static final int MAX_OPEN_CHALLENGES = 20;
    static final int RECENT_GAMES = 10;
    /** Advisory lock namespace: one player's games are started one at a time. */
    private static final int PLAYER_LOCK = 727_274;

    private final OnlineGameRepository onlineGames;
    private final OnlineChallengeRepository challenges;
    private final UserRepository users;
    private final GameService games;
    private final RatingService ratings;
    private final RatingHistoryRepository history;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;

    public OnlineGameService(OnlineGameRepository onlineGames, OnlineChallengeRepository challenges, UserRepository users,
                             GameService games, RatingService ratings, RatingHistoryRepository history,
                             ApplicationEventPublisher events, JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.onlineGames = onlineGames;
        this.challenges = challenges;
        this.users = users;
        this.games = games;
        this.ratings = ratings;
        this.history = history;
        this.events = events;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.readOnly = new TransactionTemplate(txManager);
        this.readOnly.setReadOnly(true);
    }

    // ---- reading ------------------------------------------------------------------------------------------

    public Lobby lobby(CurrentUser actor) {
        sweep();
        Instant now = Instant.now();
        return readOnly.execute(s -> {
            List<OnlineChallenge> open = challenges.findOpen(now);
            List<ChallengeDto> toMe = new ArrayList<>();
            List<ChallengeDto> mine = new ArrayList<>();
            List<ChallengeDto> anyone = new ArrayList<>();
            for (OnlineChallenge c : open) {
                ChallengeDto dto = dto(c);
                if (c.getCreatedBy().getId() == actor.id()) {
                    mine.add(dto);
                } else if (c.getOpponent() == null) {
                    anyone.add(dto);
                } else if (c.getOpponent().getId() == actor.id()) {
                    toMe.add(dto);
                }
            }
            List<OnlineGame> active = onlineGames.findActive();
            List<OnlineGameDto> playing = dtos(active, actor, now);
            List<OnlineGameDto> myGames = playing.stream().filter(g -> g.myColour() != null).toList();
            List<OnlineGameDto> myTurn = myGames.stream().filter(g -> g.myColour() == g.turn())
                    .sorted(Comparator.comparingInt((OnlineGameDto g) -> g.timeControl().kind() == TimeControl.Kind.LIVE ? 0 : 1))
                    .toList();
            List<OnlineGameDto> recent = dtos(onlineGames.findEndedFor(actor.id(), PageRequest.of(0, RECENT_GAMES)), actor, now);
            return new Lobby(toMe, mine, anyone, myGames, myTurn, playing, recent);
        });
    }

    public OnlineGameDto get(long id, CurrentUser actor) {
        Instant now = Instant.now();
        OnlineGame peek = readOnly.execute(s -> onlineGames.findWithPlayers(id).orElseThrow(OnlineGameService::notFound));
        if (peek.active() && due(peek.clock(), now)) {
            tx.executeWithoutResult(s -> settle(lockGame(id), Instant.now()));
        }
        return readOnly.execute(s -> dto(loadGame(id), actor, Instant.now()));
    }

    /** Ends games whose time is up. Also run by the lobby, so it's right even if the scheduler never ran. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void sweep() {
        Instant now = Instant.now();
        List<Long> due = readOnly.execute(s -> onlineGames.findActive().stream()
                .filter(g -> due(g.clock(), now)).map(OnlineGame::getId).toList());
        for (Long id : due) {
            tx.executeWithoutResult(s -> settle(lockGame(id), Instant.now()));
        }
    }

    private static boolean due(Clock c, Instant now) {
        return c.abortDue(now) || c.timedOut(now) != null;
    }

    /** Ends the game if its time is up. Returns whether it did. Call with the row locked. */
    private boolean settle(OnlineGame g, Instant now) {
        if (!g.active()) {
            return false;
        }
        Clock c = g.clock();
        if (c.abortDue(now)) {
            g.abort(now);
            return true;
        }
        Color flagged = c.timedOut(now);
        if (flagged != null) {
            finish(g, Ending.timeout(g.board().position(), flagged), now);
            return true;
        }
        return false;
    }

    // ---- challenges ---------------------------------------------------------------------------------------

    public ChallengeDto challenge(ChallengeRequest req, CurrentUser actor) {
        TimeControl tc;
        try {
            tc = req.timeControl();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("online.bad_time_control", e.getMessage());
        }
        return tx.execute(s -> create(actor.id(), req.opponentId(), req.colour(), req.rated(), tc));
    }

    private ChallengeDto create(long creatorId, Long opponentId, OnlineChallenge.Colour colour, boolean rated, TimeControl tc) {
        if (opponentId != null && opponentId == creatorId) {
            throw ApiException.badRequest("game.same_player", "You can't challenge yourself");
        }
        Instant now = Instant.now();
        if (challenges.countOpenBy(creatorId, now) >= MAX_OPEN_CHALLENGES) {
            throw ApiException.conflict("online.too_many_challenges", "You have too many open challenges");
        }
        User creator = activeUser(creatorId);
        User opponent = opponentId == null ? null : activeUser(opponentId);
        Instant expires = now.plus(tc.live() ? LIVE_CHALLENGE_LIFETIME : DAILY_CHALLENGE_LIFETIME);
        OnlineChallenge c = challenges.save(new OnlineChallenge(creator, opponent, colour, rated, tc, now, expires));
        if (opponent != null) {
            events.publishEvent(new ChallengeReceived(c.getId(), opponent.getId(), creator.getRealName(), tc.label(), rated));
        }
        return dto(c);
    }

    public OnlineGameDto accept(long challengeId, CurrentUser actor) {
        long id = tx.execute(s -> {
            OnlineChallenge c = lockChallenge(challengeId);
            Instant now = Instant.now();
            if (!c.openAt(now)) {
                throw closed();
            }
            if (c.getCreatedBy().getId() == actor.id()) {
                throw ApiException.badRequest("online.own_challenge", "That's your own challenge");
            }
            if (c.getOpponent() != null && c.getOpponent().getId() != actor.id()) {
                throw ApiException.forbidden("online.not_for_you", "That challenge is for someone else");
            }
            long creatorId = c.getCreatedBy().getId();
            TimeControl tc = c.timeControl();
            if (tc.live()) {
                // One clock game at a time: lock both players (in id order, so two accepts can't deadlock),
                // end any game whose time ran out, then check
                List<Long> ids = List.of(Math.min(creatorId, actor.id()), Math.max(creatorId, actor.id()));
                ids.forEach(u -> jdbc.execute("SELECT pg_advisory_xact_lock(" + PLAYER_LOCK + ", " + u.intValue() + ")"));
                for (Long active : onlineGames.findActiveIdsFor(ids)) {
                    settle(lockGame(active), now);
                }
                onlineGames.flush();
                if (onlineGames.anyLiveActiveFor(ids)) {
                    throw ApiException.conflict("online.already_playing", "One of you is already playing a game with a clock");
                }
                // Settling may have recomputed ratings, which detaches everything loaded so far
                c = lockChallenge(challengeId);
            }
            User creator = activeUser(creatorId);
            User taker = activeUser(actor.id());
            boolean creatorWhite = switch (c.getColour()) {
                case WHITE -> true;
                case BLACK -> false;
                case RANDOM -> ThreadLocalRandom.current().nextBoolean();
            };
            OnlineGame g = onlineGames.save(new OnlineGame(creatorWhite ? creator : taker, creatorWhite ? taker : creator,
                    creator, c.getId(), c.isRated(), tc, now));
            c.accept(g.getId());
            return g.getId();
        });
        return readOnly.execute(s -> dto(loadGame(id), actor, Instant.now()));
    }

    public void decline(long challengeId, CurrentUser actor) {
        tx.executeWithoutResult(s -> {
            OnlineChallenge c = lockChallenge(challengeId);
            if (c.getOpponent() == null || c.getOpponent().getId() != actor.id()) {
                throw ApiException.forbidden("online.not_for_you", "Only the person challenged can decline");
            }
            if (!c.openAt(Instant.now())) {
                throw closed();
            }
            c.decline();
        });
    }

    public void cancel(long challengeId, CurrentUser actor) {
        tx.executeWithoutResult(s -> {
            OnlineChallenge c = lockChallenge(challengeId);
            if (c.getCreatedBy().getId() != actor.id() && !actor.admin()) {
                throw ApiException.forbidden("online.not_yours", "Only whoever made the challenge can withdraw it");
            }
            if (!c.openAt(Instant.now())) {
                throw closed();
            }
            c.cancel();
        });
    }

    /** The same game again with colours swapped, as a challenge to the opponent. */
    public ChallengeDto rematch(long gameId, CurrentUser actor) {
        return tx.execute(s -> {
            OnlineGame g = loadGame(gameId);
            Color mine = g.colourOf(actor.id());
            if (mine == null) {
                throw notAPlayer();
            }
            if (g.active()) {
                throw ApiException.conflict("online.still_playing", "The game isn't over yet");
            }
            OnlineChallenge.Colour colour = mine == Color.WHITE ? OnlineChallenge.Colour.BLACK : OnlineChallenge.Colour.WHITE;
            return create(actor.id(), g.player(mine.other()).getId(), colour, g.isRated(), g.timeControl());
        });
    }

    // ---- playing ------------------------------------------------------------------------------------------

    public OnlineGameDto move(long id, MoveRequest req, CurrentUser actor) {
        return command(id, actor, (g, me, now) -> {
            Clock clock = g.clock();
            if (req.ply() != g.getPlies()) {
                throw ApiException.conflict("online.out_of_date", "The board has changed since that move was made");
            }
            if (clock.toMove() != me) {
                throw ApiException.conflict("online.not_your_turn", "It's not your move");
            }
            if (clock.wouldFlag(now, req.thinkMs())) {
                finish(g, Ending.timeout(g.board().position(), me), now);
                return Result.GAME_OVER;
            }
            ChessGame board = g.board();
            if (board.play(req.uci()).isEmpty()) {
                throw ApiException.badRequest("online.illegal_move", "That move isn't legal here").with("move", req.uci());
            }
            g.moved(board, clock.afterMove(now, req.thinkMs()));
            Ending ending = Ending.onBoard(board);
            if (ending != null) {
                finish(g, ending, now);
            } else if (!g.timeControl().live()) {
                User mover = g.player(me);
                events.publishEvent(new YourMove(g.getId(), g.player(me.other()).getId(), mover.getRealName(),
                        board.sans().getLast()));
            }
            return Result.OK;
        });
    }

    public OnlineGameDto resign(long id, CurrentUser actor) {
        return command(id, actor, (g, me, now) -> {
            // Before both have moved there's nothing to lose yet: the game is just called off
            if (g.clock().abortable()) {
                g.abort(now);
            } else {
                finish(g, Ending.resignation(me), now);
            }
            return Result.OK;
        });
    }

    public OnlineGameDto abort(long id, CurrentUser actor) {
        return command(id, actor, (g, me, now) -> {
            if (!g.clock().abortable()) {
                throw ApiException.conflict("online.cannot_abort", "Both players have moved: resign or offer a draw instead");
            }
            g.abort(now);
            return Result.OK;
        });
    }

    public OnlineGameDto offerDraw(long id, CurrentUser actor) {
        return command(id, actor, (g, me, now) -> {
            long opponent = g.player(me.other()).getId();
            if (Objects.equals(g.getDrawOfferBy(), opponent)) {
                finish(g, Ending.agreement(), now); // both want a draw
            } else if (!Objects.equals(g.getDrawOfferBy(), actor.id())) {
                g.offerDraw(actor.id());
            }
            return Result.OK;
        });
    }

    public OnlineGameDto acceptDraw(long id, CurrentUser actor) {
        return command(id, actor, (g, me, now) -> {
            if (!Objects.equals(g.getDrawOfferBy(), g.player(me.other()).getId())) {
                throw noDrawOffer();
            }
            finish(g, Ending.agreement(), now);
            return Result.OK;
        });
    }

    public OnlineGameDto declineDraw(long id, CurrentUser actor) {
        return command(id, actor, (g, me, now) -> {
            if (!Objects.equals(g.getDrawOfferBy(), g.player(me.other()).getId())) {
                throw noDrawOffer();
            }
            g.clearDrawOffer();
            return Result.OK;
        });
    }

    private enum Result { OK, GAME_OVER }

    @FunctionalInterface
    private interface Command {
        Result apply(OnlineGame game, Color me, Instant now);
    }

    /**
     * Runs a player's command on a locked, up-to-date game: settles the clock first, so a game whose
     * time ran out ends (and stays ended) even though the command is then refused.
     */
    private OnlineGameDto command(long id, CurrentUser actor, Command command) {
        Result result = tx.execute(s -> {
            OnlineGame g = lockGame(id);
            Color me = g.colourOf(actor.id());
            if (me == null) {
                throw notAPlayer();
            }
            Instant now = Instant.now();
            if (settle(g, now) || !g.active()) {
                return Result.GAME_OVER;
            }
            return command.apply(g, me, now);
        });
        if (result == Result.GAME_OVER) {
            throw ApiException.conflict("online.game_over", "This game has ended");
        }
        return readOnly.execute(s -> dto(loadGame(id), actor, Instant.now()));
    }

    /**
     * Ends the game with a result. A rated game becomes a confirmed game and the online ratings are
     * recomputed, which clears the persistence context: callers reload the game afterwards.
     */
    private void finish(OnlineGame g, Ending ending, Instant now) {
        g.finish(ending, now);
        if (g.isRated()) {
            long gameId = games.recordOnline(g.getWhite().getId(), g.getBlack().getId(), g.getCreatedBy().getId(),
                    ending.result(), now, g.getMoves(), g.timeControl().label());
            g.becameGame(gameId);
            ratings.recomputeFrom(now);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private OnlineGame lockGame(long id) {
        return onlineGames.findByIdForUpdate(id).orElseThrow(OnlineGameService::notFound);
    }

    private OnlineGame loadGame(long id) {
        return onlineGames.findWithPlayers(id).orElseThrow(OnlineGameService::notFound);
    }

    private OnlineChallenge lockChallenge(long id) {
        return challenges.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("online.challenge_not_found", "Challenge not found"));
    }

    private User activeUser(long id) {
        return users.findById(id).filter(User::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("player.unknown_or_disabled", "Unknown or disabled player"));
    }

    private static ChallengeDto dto(OnlineChallenge c) {
        return new ChallengeDto(c.getId(), PlayerRef.of(c.getCreatedBy(), GameMode.ONLINE),
                PlayerRef.of(c.getOpponent(), GameMode.ONLINE), c.getColour(), c.isRated(),
                TimeControlDto.of(c.timeControl()), c.getCreatedAt(), c.getExpiresAt());
    }

    private OnlineGameDto dto(OnlineGame g, CurrentUser actor, Instant now) {
        return dtos(List.of(g), actor, now).getFirst();
    }

    private List<OnlineGameDto> dtos(Collection<OnlineGame> list, CurrentUser actor, Instant now) {
        List<Long> gameIds = list.stream().map(OnlineGame::getGameId).filter(Objects::nonNull).toList();
        Map<Long, List<RatingHistory>> byGame = gameIds.isEmpty() ? new HashMap<>()
                : history.findByGameIdIn(gameIds).stream().collect(Collectors.groupingBy(RatingHistory::getGameId));
        return list.stream().map(g -> {
            Clock c = g.clock();
            ChessGame board = g.board();
            boolean liveClock = g.timeControl().live();
            Instant firstMoveBy = c.abortable() ? (liveClock ? c.turnStartedAt().plus(Clock.FIRST_MOVE_LIMIT) : c.deadline()) : null;
            ClockDto clock = new ClockDto(c.remainingMs(Color.WHITE, now), c.remainingMs(Color.BLACK, now), c.running(),
                    c.toMove(), now, firstMoveBy, c.running() ? c.deadline() : null);
            Color mine = g.colourOf(actor.id());
            return new OnlineGameDto(g.getId(), PlayerRef.of(g.getWhite(), GameMode.ONLINE),
                    PlayerRef.of(g.getBlack(), GameMode.ONLINE), g.isRated(), TimeControlDto.of(g.timeControl()),
                    g.getStatus(), g.getResult(), g.getEndReason(), g.getMoves(), g.getPlies(), g.getLastMove(),
                    board.position().fen(), board.position().turn(), clock, g.getDrawOfferBy(), mine,
                    mine != null && c.abortable(), g.getGameId(), ratingChange(g, byGame.get(g.getGameId())),
                    g.getStartedAt(), g.getEndedAt(), g.getVersion());
        }).toList();
    }

    private static RatingChange ratingChange(OnlineGame g, List<RatingHistory> rows) {
        if (rows == null) {
            return null;
        }
        RatingDelta white = null;
        RatingDelta black = null;
        for (RatingHistory h : rows) {
            RatingDelta d = new RatingDelta((int) Math.round(h.getRatingBefore()), (int) Math.round(h.getRatingAfter()));
            if (h.getUserId().equals(g.getWhite().getId())) {
                white = d;
            } else if (h.getUserId().equals(g.getBlack().getId())) {
                black = d;
            }
        }
        return white == null || black == null ? null : new RatingChange(white, black);
    }

    private static ApiException notFound() {
        return ApiException.notFound("online.not_found", "Online game not found");
    }

    private static ApiException notAPlayer() {
        return ApiException.forbidden("online.not_a_player", "Only the players can do that");
    }

    private static ApiException closed() {
        return ApiException.conflict("online.challenge_closed", "That challenge has been taken, withdrawn or has expired");
    }

    private static ApiException noDrawOffer() {
        return ApiException.conflict("online.no_draw_offer", "Your opponent hasn't offered a draw");
    }
}
