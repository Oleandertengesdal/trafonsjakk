package no.trafonsjakk.game;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.game.GameDtos.CreateGameRequest;
import no.trafonsjakk.game.GameDtos.GameDto;
import no.trafonsjakk.game.GameDtos.ResolveAction;
import no.trafonsjakk.game.GameDtos.ResolveRequest;
import no.trafonsjakk.rating.RatingService;
import no.trafonsjakk.tournament.MatchStatus;
import no.trafonsjakk.tournament.TournamentMatch;
import no.trafonsjakk.tournament.TournamentMatchRepository;
import no.trafonsjakk.tournament.TournamentService;
import no.trafonsjakk.tournament.TournamentStatus;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameService {

    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(10);
    private static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Set<GameStatus> VISIBLE = EnumSet.of(GameStatus.PENDING, GameStatus.CONFIRMED,
            GameStatus.DISPUTED);

    private final GameRepository games;
    private final UserRepository users;
    private final TournamentMatchRepository matches;
    private final TournamentService tournaments;
    private final RatingService ratings;
    private final GameMapper mapper;
    private final AppProperties props;

    public GameService(GameRepository games, UserRepository users, TournamentMatchRepository matches,
                       TournamentService tournaments, RatingService ratings, GameMapper mapper,
                       AppProperties props) {
        this.props = props;
        this.games = games;
        this.users = users;
        this.matches = matches;
        this.tournaments = tournaments;
        this.ratings = ratings;
        this.mapper = mapper;
    }

    // ---- queries -------------------------------------------------------------------------------

    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    @Transactional(readOnly = true)
    public PageDto<GameDto> list(String username, GameStatus status, int page, int size, CurrentUser actor) {
        Set<GameStatus> statuses = status != null ? EnumSet.of(status) : VISIBLE;
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        Page<Game> result;
        if (username != null && !username.isBlank()) {
            User player = users.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found"));
            result = games.findByPlayerAndStatuses(player.getId(), statuses, pr);
        } else {
            result = games.findByStatuses(statuses, pr);
        }
        return new PageDto<>(mapper.toDtos(result.getContent(), actor), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<GameDto> awaitingMyConfirmation(CurrentUser actor) {
        return mapper.toDtos(games.findAwaitingConfirmationBy(actor.id()), actor);
    }

    @Transactional(readOnly = true)
    public GameDto get(long id, CurrentUser actor) {
        return mapper.toDto(load(id), actor);
    }

    @Transactional(readOnly = true)
    public List<GameDto> disputed(CurrentUser actor) {
        return mapper.toDtos(games.findByStatusOrderByCreatedAtDesc(GameStatus.DISPUTED), actor);
    }

    /**
     * Pending results where a player's account is disabled or deleted. They're never auto-confirmed
     * (that player can't object any more), so an admin confirms or voids them.
     */
    @Transactional(readOnly = true)
    public List<GameDto> held(CurrentUser actor) {
        return mapper.toDtos(games.findPendingWithInactivePlayer(), actor);
    }

    // ---- commands ------------------------------------------------------------------------------

    @Transactional
    public GameDto create(CreateGameRequest req, CurrentUser actor) {
        if (req.whiteId().equals(req.blackId())) {
            throw ApiException.badRequest("game.same_player", "White and Black must be different players");
        }
        if (!actor.admin() && actor.id() != req.whiteId() && actor.id() != req.blackId()) {
            throw ApiException.forbidden("game.not_a_player", "You can only log games you played in");
        }
        Instant now = Instant.now();
        if (req.playedAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw ApiException.badRequest("game.in_future", "The game can't be in the future");
        }
        if (req.playedAt().isBefore(EARLIEST)) {
            throw ApiException.badRequest("game.too_old", "That date is too far in the past");
        }
        // A back-dated game replays every rating after it, so members log recent games; an admin enters old ones.
        if (!actor.admin() && req.playedAt().isBefore(now.minus(props.games().maxBackdate()))) {
            throw ApiException.badRequest("game.too_old", "That date is too far in the past");
        }
        // An admin may finish a tournament match for someone disabled midway (e.g. a forfeit);
        // otherwise new games need active players.
        boolean adminTournamentGame = actor.admin() && req.matchId() != null;
        User white = adminTournamentGame ? existingUser(req.whiteId()) : activeUser(req.whiteId());
        User black = adminTournamentGame ? existingUser(req.blackId()) : activeUser(req.blackId());
        User reporter = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));

        Game game = new Game(white, black, req.result(), req.playedAt(), reporter);
        game.setOpening(blankToNull(req.opening()));
        game.setEco(req.eco() == null || req.eco().isBlank() ? null : req.eco().toUpperCase(Locale.ROOT));
        game.setNotes(blankToNull(req.notes()));
        game.setMoves(blankToNull(req.moves()));

        if (req.matchId() != null) {
            TournamentMatch match = matches.findByIdForUpdate(req.matchId())
                    .orElseThrow(() -> ApiException.badRequest("match.not_found", "Tournament match not found"));
            if (match.getTournament().getStatus() != TournamentStatus.IN_PROGRESS) {
                throw ApiException.conflict("match.tournament_not_running", "That tournament isn't in progress");
            }
            if (match.getStatus() != MatchStatus.READY) {
                throw ApiException.conflict("match.not_ready", "That match isn't ready to be played");
            }
            if (match.getWhite() == null || match.getBlack() == null
                    || !Set.of(match.getWhite().getId(), match.getBlack().getId())
                    .equals(Set.of(white.getId(), black.getId()))) {
                throw ApiException.badRequest("match.wrong_players", "The players don't match the tournament pairing");
            }
            if (games.existsOpenForMatch(match.getId())) {
                throw ApiException.conflict("match.result_pending", "A result for this match is already waiting for confirmation");
            }
            game.setMatch(match);
        }
        return mapper.toDto(games.save(game), actor);
    }

    @Transactional
    public GameDto confirm(long id, CurrentUser actor) {
        Game game = load(id);
        requireStatus(game, GameStatus.PENDING);
        if (!game.canBeConfirmedBy(actor.id())) {
            throw ApiException.forbidden("game.not_opponent", "Only the opponent can confirm this result");
        }
        game.confirm(users.getReferenceById(actor.id()), Instant.now());
        afterConfirmedSetChanged(game);
        return reload(id, actor);
    }

    @Transactional
    public GameDto dispute(long id, String reason, CurrentUser actor) {
        Game game = load(id);
        requireStatus(game, GameStatus.PENDING);
        if (!game.canBeConfirmedBy(actor.id())) {
            throw ApiException.forbidden("game.not_opponent", "Only the opponent can dispute this result");
        }
        game.dispute(reason.trim());
        return mapper.toDto(game, actor);
    }

    @Transactional
    public GameDto withdraw(long id, CurrentUser actor) {
        Game game = load(id);
        requireStatus(game, GameStatus.PENDING);
        if (game.getReportedBy().getId() != actor.id() && !actor.admin()) {
            throw ApiException.forbidden("game.not_reporter", "Only the person who logged this game can withdraw it");
        }
        game.voidGame();
        return mapper.toDto(game, actor);
    }

    /** Admin: confirm (optionally correcting the result) or void any non-voided game. */
    @Transactional
    public GameDto resolve(long id, ResolveRequest req, CurrentUser actor) {
        Game game = load(id);
        if (game.getStatus() == GameStatus.VOIDED) {
            throw ApiException.conflict("game.voided", "This game has been voided");
        }
        boolean wasConfirmed = game.getStatus() == GameStatus.CONFIRMED;
        if (req.action() == ResolveAction.CONFIRM) {
            boolean changedResult = req.result() != null && req.result() != game.getResult();
            if (req.result() != null) {
                game.setResult(req.result());
            }
            if (!wasConfirmed) {
                game.confirm(users.getReferenceById(actor.id()), Instant.now());
            }
            if (!wasConfirmed || changedResult) {
                afterConfirmedSetChanged(game);
                return reload(id, actor);
            }
            return mapper.toDto(game, actor);
        }
        game.voidGame();
        if (wasConfirmed) {
            afterConfirmedSetChanged(game);
            return reload(id, actor);
        }
        return mapper.toDto(game, actor);
    }

    /**
     * Confirms PENDING games older than the auto-confirm window. Called by {@link AutoConfirmService}.
     * Games with a disabled or deleted player are left for an admin (see {@link #held}).
     * Returns the number of games confirmed.
     */
    @Transactional
    public int confirmOverdue(Instant cutoff) {
        List<Game> overdue = games.findOverduePending(cutoff);
        if (overdue.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        Instant earliest = overdue.stream().map(Game::getPlayedAt).min(Comparator.naturalOrder()).orElseThrow();
        for (Game g : overdue) {
            g.confirm(null, now);
        }
        games.flush();
        for (Game g : overdue) {
            if (g.getMatch() != null) {
                tournaments.reevaluateMatch(g.getMatch().getId());
            }
        }
        ratings.recomputeFrom(earliest);
        return overdue.size();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void afterConfirmedSetChanged(Game game) {
        Instant playedAt = game.getPlayedAt();
        games.flush();
        if (game.getMatch() != null) {
            tournaments.reevaluateMatch(game.getMatch().getId());
        }
        ratings.recomputeFrom(playedAt); // clears the persistence context
    }

    private GameDto reload(long id, CurrentUser actor) {
        return mapper.toDto(load(id), actor);
    }

    /** Adds, changes or clears the moves of a game. Either player or an admin; doesn't affect ratings. */
    @Transactional
    public GameDto updateMoves(long id, String moves, CurrentUser actor) {
        Game game = load(id);
        if (game.getStatus() == GameStatus.VOIDED) {
            throw ApiException.conflict("game.voided", "This game has been voided");
        }
        if (!game.involves(actor.id()) && !actor.admin()) {
            throw ApiException.forbidden("game.not_a_player", "Only the players (or an admin) can add the moves");
        }
        game.setMoves(moves == null || moves.isBlank() ? null : moves.trim());
        return mapper.toDto(game, actor);
    }

    private Game load(long id) {
        return games.findById(id).orElseThrow(() -> ApiException.notFound("game.not_found", "Game not found"));
    }

    private User existingUser(long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.badRequest("player.unknown_or_disabled", "Unknown or disabled player"));
    }

    private User activeUser(long id) {
        return users.findById(id).filter(User::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("player.unknown_or_disabled", "Unknown or disabled player"));
    }

    private static void requireStatus(Game game, GameStatus expected) {
        if (game.getStatus() != expected) {
            throw ApiException.conflict("game.wrong_status", "This game is already " + game.getStatus().name().toLowerCase(Locale.ROOT))
                    .with("status", game.getStatus().name());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
