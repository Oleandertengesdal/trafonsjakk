-- Invite codes for several people: a limit and a counter, plus who used each code.
ALTER TABLE invite_codes
    ADD COLUMN max_uses  INT NOT NULL DEFAULT 1,
    ADD COLUMN use_count INT NOT NULL DEFAULT 0;

CREATE TABLE invite_redemptions (
    id         BIGSERIAL PRIMARY KEY,
    invite_id  BIGINT      NOT NULL REFERENCES invite_codes (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    used_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_invite_redemptions_invite ON invite_redemptions (invite_id, used_at);

-- Keep the history of the existing single-use codes
INSERT INTO invite_redemptions (invite_id, user_id, used_at)
SELECT id, used_by, used_at FROM invite_codes WHERE used_by IS NOT NULL AND used_at IS NOT NULL;
UPDATE invite_codes SET use_count = 1 WHERE used_at IS NOT NULL;

ALTER TABLE invite_codes
    DROP COLUMN used_by,
    DROP COLUMN used_at,
    ADD CONSTRAINT ck_invite_codes_uses CHECK (max_uses BETWEEN 1 AND 500 AND use_count BETWEEN 0 AND max_uses);

-- One-time password reset links made by an admin. Only a hash of the token is stored.
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_by  BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);

-- Comments on games
CREATE TABLE game_comments (
    id         BIGSERIAL PRIMARY KEY,
    game_id    BIGINT        NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    author_id  BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    edited_at  TIMESTAMPTZ
);
CREATE INDEX ix_game_comments_game ON game_comments (game_id, created_at);

-- Kudos: one per person per game
CREATE TABLE game_kudos (
    game_id    BIGINT      NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, user_id)
);
