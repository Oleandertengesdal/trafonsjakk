package no.trafonsjakk.live;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.GameDtos.CreateGameRequest;
import no.trafonsjakk.game.GameDtos.GameDto;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.game.GameService;
import no.trafonsjakk.live.LiveDtos.LiveGameDto;
import no.trafonsjakk.live.LiveDtos.StartLiveRequest;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Games being played right now. Either player (or an admin) records the moves on a phone as they're
 * played; everyone else, and the TV screen, follows by polling. Moves are optional: a live game can end
 * with none. Finishing turns it into an ordinary game that the opponent confirms as usual.
 */
@Service
public class LiveGameService {

    /** A live game nobody has saved for this long is treated as left behind. */
    static final Duration IDLE_LIMIT = Duration.ofHours(6);

    private final LiveGameRepository live;
    private final UserRepository users;
    private final GameService games;

    public LiveGameService(LiveGameRepository live, UserRepository users, GameService games) {
        this.live = live;
        this.users = users;
        this.games = games;
    }

    // The two reads below are polled every few seconds by everyone watching (and by the TV screen), so
    // they only read: games left behind are filtered out here and marked ABANDONED by the sweep below.

    @Transactional(readOnly = true)
    public List<LiveGameDto> running(CurrentUser actor) {
        return live.findRunning(Instant.now().minus(IDLE_LIMIT)).stream().map(g -> LiveGameDto.of(g, actor)).toList();
    }

    @Transactional(readOnly = true)
    public LiveGameDto get(long id, CurrentUser actor) {
        LiveGame g = live.findWithPlayers(id).orElseThrow(LiveGameService::notFound);
        LiveGameDto dto = LiveGameDto.of(g, actor);
        boolean leftBehind = g.getStatus() == LiveStatus.LIVE && g.getUpdatedAt().isBefore(Instant.now().minus(IDLE_LIMIT));
        // Not swept yet: show it as what it is about to become
        return !leftBehind ? dto : new LiveGameDto(dto.id(), dto.white(), dto.black(), LiveStatus.ABANDONED, dto.moves(),
                dto.version(), dto.startedAt(), dto.updatedAt(), dto.gameId(), dto.canRecord());
    }

    /** Marks games nobody has saved for {@link #IDLE_LIMIT} as abandoned. Also done before a new game starts. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    @Transactional
    public void abandonIdle() {
        live.abandonIdleSince(Instant.now().minus(IDLE_LIMIT));
    }

    @Transactional
    public LiveGameDto start(StartLiveRequest req, CurrentUser actor) {
        if (req.whiteId().equals(req.blackId())) {
            throw ApiException.badRequest("game.same_player", "White and Black must be different players");
        }
        if (!actor.admin() && actor.id() != req.whiteId() && actor.id() != req.blackId()) {
            throw ApiException.forbidden("live.not_a_player", "You can only start a live game you're playing in");
        }
        Instant now = Instant.now();
        live.abandonIdleSince(now.minus(IDLE_LIMIT));
        if (live.anyRunningFor(List.of(req.whiteId(), req.blackId()))) {
            throw ApiException.conflict("live.already_playing", "One of the players is already in a live game");
        }
        User white = activeUser(req.whiteId());
        User black = activeUser(req.blackId());
        User starter = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
        return LiveGameDto.of(live.save(new LiveGame(white, black, starter, now)), actor);
    }

    @Transactional
    public LiveGameDto saveMoves(long id, String moves, long version, CurrentUser actor) {
        LiveGame g = runningForRecorder(id, actor);
        if (g.getVersion() != version) {
            throw ApiException.conflict("live.out_of_date", "Someone else saved moves in the meantime");
        }
        g.saveMoves(moves == null || moves.isBlank() ? null : moves.trim(), Instant.now());
        return LiveGameDto.of(g, actor);
    }

    /** Logs the result as an ordinary game (with the moves, if any), played when the live game started. */
    @Transactional
    public GameDto finish(long id, GameResult result, CurrentUser actor) {
        LiveGame g = runningForRecorder(id, actor);
        GameDto game = games.create(new CreateGameRequest(g.getWhite().getId(), g.getBlack().getId(), result,
                g.getStartedAt(), null, null, null, g.getMoves(), null), actor);
        g.finish(game.id(), Instant.now());
        return game;
    }

    @Transactional
    public void abandon(long id, CurrentUser actor) {
        runningForRecorder(id, actor).abandon(Instant.now());
    }

    private LiveGame runningForRecorder(long id, CurrentUser actor) {
        LiveGame g = live.findByIdForUpdate(id).orElseThrow(LiveGameService::notFound);
        if (!actor.admin() && !g.hasPlayer(actor.id())) {
            throw ApiException.forbidden("live.not_a_player", "Only the players (or an admin) can record this game");
        }
        if (g.getStatus() != LiveStatus.LIVE || g.getUpdatedAt().isBefore(Instant.now().minus(IDLE_LIMIT))) {
            throw ApiException.conflict("live.not_running", "This game has already ended");
        }
        return g;
    }

    private User activeUser(long id) {
        return users.findById(id).filter(User::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("player.unknown_or_disabled", "Unknown or disabled player"));
    }

    private static ApiException notFound() {
        return ApiException.notFound("live.not_found", "Live game not found");
    }
}
