package no.trafonsjakk.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import no.trafonsjakk.user.User;

/**
 * A comment on a game, or a reply in a comment's thread (one level: {@code parentId} is always the
 * thread's first comment). A first comment that has replies is "soft" deleted so the thread stays readable.
 */
@Entity
@Table(name = "game_comments")
public class GameComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "parent_id")
    private Long parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_user_id")
    private User replyTo;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected GameComment() {
    }

    public GameComment(Long gameId, User author, String body, Instant createdAt) {
        this.gameId = gameId;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public GameComment(Long gameId, User author, String body, Instant createdAt, Long parentId, User replyTo) {
        this(gameId, author, body, createdAt);
        this.parentId = parentId;
        this.replyTo = replyTo;
    }

    /** Keeps the row (its replies hang off it) but removes the text. */
    public void softDelete(Instant at) {
        this.body = "";
        this.deletedAt = at;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getParentId() {
        return parentId;
    }

    public User getReplyTo() {
        return replyTo;
    }

    public void edit(String body, Instant at) {
        this.body = body;
        this.editedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getGameId() {
        return gameId;
    }

    public User getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
