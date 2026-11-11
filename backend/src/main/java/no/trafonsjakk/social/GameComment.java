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

/** A comment on a game. */
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

    protected GameComment() {
    }

    public GameComment(Long gameId, User author, String body, Instant createdAt) {
        this.gameId = gameId;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
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
