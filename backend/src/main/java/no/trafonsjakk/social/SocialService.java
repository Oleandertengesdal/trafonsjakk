package no.trafonsjakk.social;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.game.GameStatus;
import no.trafonsjakk.social.SocialDtos.CommentDto;
import no.trafonsjakk.social.SocialDtos.KudosDto;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments and kudos on games. Every member can see and use them on every game. */
@Service
public class SocialService {

    private final GameRepository games;
    private final GameCommentRepository comments;
    private final KudosRepository kudos;
    private final UserRepository users;

    public SocialService(GameRepository games, GameCommentRepository comments, KudosRepository kudos, UserRepository users) {
        this.games = games;
        this.comments = comments;
        this.kudos = kudos;
        this.users = users;
    }

    // ---- comments ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CommentDto> comments(long gameId, CurrentUser actor) {
        game(gameId);
        return comments.findByGame(gameId).stream().map(c -> toDto(c, actor)).toList();
    }

    @Transactional
    public CommentDto add(long gameId, String body, CurrentUser actor) {
        Game g = game(gameId);
        if (g.getStatus() == GameStatus.VOIDED) {
            throw ApiException.conflict("game.voided", "This game has been voided");
        }
        User author = users.getReferenceById(actor.id());
        GameComment saved = comments.save(new GameComment(gameId, author, body.trim(), Instant.now()));
        return toDto(comments.findById(saved.getId()).orElseThrow(), actor);
    }

    /** Only the author edits (admins can delete, not put words in someone's mouth). */
    @Transactional
    public CommentDto edit(long gameId, long commentId, String body, CurrentUser actor) {
        GameComment c = comment(gameId, commentId);
        if (c.getAuthor().getId() != actor.id()) {
            throw ApiException.forbidden("comment.not_author", "You can only change your own comments");
        }
        c.edit(body.trim(), Instant.now());
        return toDto(c, actor);
    }

    @Transactional
    public void delete(long gameId, long commentId, CurrentUser actor) {
        GameComment c = comment(gameId, commentId);
        if (c.getAuthor().getId() != actor.id() && !actor.admin()) {
            throw ApiException.forbidden("comment.not_author", "You can only delete your own comments");
        }
        comments.delete(c);
    }

    // ---- kudos ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public KudosDto kudos(long gameId, CurrentUser actor) {
        game(gameId);
        return kudosOf(gameId, actor);
    }

    /** Idempotent: giving twice (double click, two tabs) still counts once. */
    @Transactional
    public KudosDto give(long gameId, CurrentUser actor) {
        Game g = game(gameId);
        if (g.involves(actor.id())) {
            throw ApiException.forbidden("kudos.own_game", "You can't give kudos to your own game");
        }
        if (g.getStatus() != GameStatus.CONFIRMED) {
            throw ApiException.conflict("kudos.not_confirmed", "Kudos can be given once the result is confirmed");
        }
        kudos.give(gameId, actor.id());
        return kudosOf(gameId, actor);
    }

    @Transactional
    public KudosDto take(long gameId, CurrentUser actor) {
        game(gameId);
        kudos.take(gameId, actor.id());
        return kudosOf(gameId, actor);
    }

    private KudosDto kudosOf(long gameId, CurrentUser actor) {
        List<Long> ids = kudos.giverIds(gameId);
        Map<Long, User> byId = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<PlayerRef> givers = ids.stream().map(byId::get).map(PlayerRef::of).toList();
        return new KudosDto(givers.size(), ids.contains(actor.id()), givers);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Game game(long id) {
        return games.findById(id).orElseThrow(() -> ApiException.notFound("game.not_found", "Game not found"));
    }

    private GameComment comment(long gameId, long commentId) {
        return comments.findById(commentId)
                .filter(c -> c.getGameId() == gameId)
                .orElseThrow(() -> ApiException.notFound("comment.not_found", "Comment not found"));
    }

    private static CommentDto toDto(GameComment c, CurrentUser actor) {
        boolean mine = c.getAuthor().getId() == actor.id();
        return new CommentDto(c.getId(), PlayerRef.of(c.getAuthor()), c.getBody(), c.getCreatedAt(), c.getEditedAt(),
                mine, mine || actor.admin());
    }
}
