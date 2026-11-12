-- Live games: a game being played right now, with its moves saved as they're made so others can follow.
-- When it ends it becomes an ordinary game (game_id), which the opponent confirms as usual.
CREATE TABLE live_games (
    id          BIGSERIAL PRIMARY KEY,
    white_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    black_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_by  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      VARCHAR(10) NOT NULL DEFAULT 'LIVE' CHECK (status IN ('LIVE', 'FINISHED', 'ABANDONED')),
    moves       TEXT,
    version     BIGINT      NOT NULL DEFAULT 0,
    game_id     BIGINT      REFERENCES games (id) ON DELETE SET NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_live_games_players CHECK (white_id <> black_id),
    CONSTRAINT ck_live_games_moves_length CHECK (moves IS NULL OR length(moves) <= 20000)
);
CREATE INDEX ix_live_games_live ON live_games (updated_at) WHERE status = 'LIVE';

-- Deleting an account keeps the games (they belong to the opponents too) but removes the person:
-- name, username, password, comments, likes and kudos. deleted_at marks it so it can't be switched back on.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
