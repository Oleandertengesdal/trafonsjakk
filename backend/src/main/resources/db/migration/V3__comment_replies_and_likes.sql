-- Replies: one level deep. parent_id is always the thread's first comment; reply_to_user_id is who was answered.
ALTER TABLE game_comments
    ADD COLUMN parent_id        BIGINT      REFERENCES game_comments (id) ON DELETE CASCADE,
    ADD COLUMN reply_to_user_id BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN deleted_at       TIMESTAMPTZ;
CREATE INDEX ix_game_comments_parent ON game_comments (parent_id);

-- Likes on comments: one per person per comment
CREATE TABLE comment_likes (
    comment_id BIGINT      NOT NULL REFERENCES game_comments (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);
