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
import no.trafonsjakk.social.SocialDtos.CommentLikes;
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
    private final CommentLikeRepository likes;

    public SocialService(GameRepository games, GameCommentRepository comments, KudosRepository kudos, UserRepository users,
                         CommentLikeRepository likes) {
        this.likes = likes;
        this.games = games;
        this.comments = comments;
        this.kudos = kudos;
        this.users = users;
    }

    // ---- comments ------------------------------------------------------------------------------

    /** Threads, oldest first, each with its replies (oldest first). */
    @Transactional(readOnly = true)
    public List<CommentDto> comments(long gameId, CurrentUser actor) {
        game(gameId);
        List<GameComment> all = comments.findByGame(gameId);
        Map<Long, List<Long>> likers = likes.likers(all.stream().map(GameComment::getId).toList());
        Map<Long, User> people = users.findAllById(likers.values().stream().flatMap(List::stream).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<GameComment>> repliesByThread = all.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(GameComment::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .filter(c -> !c.isDeleted() || repliesByThread.containsKey(c.getId()))
                .map(root -> toDto(root, actor, likers, people, repliesByThread.getOrDefault(root.getId(), List.of()).stream()
                        .map(r -> toDto(r, actor, likers, people, List.of())).toList()))
                .toList();
    }

    @Transactional
    public CommentDto add(long gameId, String body, Long parentId, CurrentUser actor) {
        Game g = game(gameId);
        if (g.getStatus() == GameStatus.VOIDED) {
            throw ApiException.conflict("game.voided", "This game has been voided");
        }
        Long threadId = null;
        User replyTo = null;
        if (parentId != null) {
            GameComment parent = comments.findById(parentId)
                    .filter(c -> c.getGameId() == gameId)
                    .orElseThrow(SocialService::commentNotFound);
            threadId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
            replyTo = parent.isDeleted() ? null : parent.getAuthor();
        }
        User author = users.getReferenceById(actor.id());
        GameComment saved = comments.save(new GameComment(gameId, author, body.trim(), Instant.now(), threadId, replyTo));
        GameComment fresh = comments.findById(saved.getId()).orElseThrow();
        return toDto(fresh, actor, Map.of(), Map.of(), List.of());
    }

    /** Only the author edits (admins can delete, not put words in someone's mouth). */
    @Transactional
    public CommentDto edit(long gameId, long commentId, String body, CurrentUser actor) {
        GameComment c = visibleComment(gameId, commentId);
        if (c.getAuthor().getId() != actor.id()) {
            throw ApiException.forbidden("comment.not_author", "You can only change your own comments");
        }
        c.edit(body.trim(), Instant.now());
        Map<Long, List<Long>> likers = likes.likers(List.of(c.getId()));
        Map<Long, User> people = users.findAllById(likers.getOrDefault(c.getId(), List.of())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return toDto(c, actor, likers, people, List.of());
    }

    /**
     * Author or admin. A thread's first comment that has replies keeps its place (without text or likes),
     * so the conversation stays readable; it disappears when its last reply is deleted.
     */
    @Transactional
    public void delete(long gameId, long commentId, CurrentUser actor) {
        GameComment c = visibleComment(gameId, commentId);
        if (c.getAuthor().getId() != actor.id() && !actor.admin()) {
            throw ApiException.forbidden("comment.not_author", "You can only delete your own comments");
        }
        if (c.getParentId() == null && comments.countByParentId(c.getId()) > 0) {
            c.softDelete(Instant.now());
            likes.deleteAll(c.getId());
            return;
        }
        comments.delete(c);
        comments.flush();
        if (c.getParentId() != null) {
            comments.findById(c.getParentId())
                    .filter(GameComment::isDeleted)
                    .filter(root -> comments.countByParentId(root.getId()) == 0)
                    .ifPresent(comments::delete);
        }
    }

    /** Idempotent. Not on your own comment, nor on a deleted one. */
    @Transactional
    public CommentLikes like(long gameId, long commentId, CurrentUser actor) {
        GameComment c = visibleComment(gameId, commentId);
        if (c.getAuthor().getId() == actor.id()) {
            throw ApiException.forbidden("comment.own_like", "You can't like your own comment");
        }
        likes.like(commentId, actor.id());
        return likesOf(commentId, actor);
    }

    @Transactional
    public CommentLikes unlike(long gameId, long commentId, CurrentUser actor) {
        visibleComment(gameId, commentId);
        likes.unlike(commentId, actor.id());
        return likesOf(commentId, actor);
    }

    private CommentLikes likesOf(long commentId, CurrentUser actor) {
        List<Long> ids = likes.likers(List.of(commentId)).getOrDefault(commentId, List.of());
        Map<Long, User> byId = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return new CommentLikes(ids.size(), ids.contains(actor.id()), ids.stream().map(byId::get).map(PlayerRef::of).toList());
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

    /** A comment of this game that hasn't been deleted. */
    private GameComment visibleComment(long gameId, long commentId) {
        return comments.findById(commentId)
                .filter(c -> c.getGameId() == gameId && !c.isDeleted())
                .orElseThrow(SocialService::commentNotFound);
    }

    private static ApiException commentNotFound() {
        return ApiException.notFound("comment.not_found", "Comment not found");
    }

    private static CommentDto toDto(GameComment c, CurrentUser actor, Map<Long, List<Long>> likers, Map<Long, User> people,
                                    List<CommentDto> replies) {
        if (c.isDeleted()) {
            return new CommentDto(c.getId(), c.getParentId(), null, null, "", c.getCreatedAt(), null, true, 0, false,
                    List.of(), false, false, false, replies);
        }
        boolean mine = c.getAuthor().getId() == actor.id();
        List<Long> ids = likers.getOrDefault(c.getId(), List.of());
        List<PlayerRef> likedBy = ids.stream().map(people::get).filter(java.util.Objects::nonNull).map(PlayerRef::of).toList();
        return new CommentDto(c.getId(), c.getParentId(), PlayerRef.of(c.getAuthor()), PlayerRef.of(c.getReplyTo()), c.getBody(),
                c.getCreatedAt(), c.getEditedAt(), false, ids.size(), ids.contains(actor.id()), likedBy, mine,
                mine || actor.admin(), !mine, replies);
    }
}
