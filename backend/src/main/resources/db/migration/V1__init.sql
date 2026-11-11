-- Trafonsjakk initial schema
-- Ratings on `users` are a cache rebuilt from CONFIRMED games (see docs/ARCHITECTURE.md §3.4).

CREATE TABLE users (
    id               BIGSERIAL PRIMARY KEY,
    username         VARCHAR(20)  NOT NULL,
    real_name        VARCHAR(60)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    rating           DOUBLE PRECISION NOT NULL DEFAULT 1500,
    rating_deviation DOUBLE PRECISION NOT NULL DEFAULT 350,
    volatility       DOUBLE PRECISION NOT NULL DEFAULT 0.06,
    rated_games      INTEGER      NOT NULL DEFAULT 0,
    peak_rating      DOUBLE PRECISION NOT NULL DEFAULT 1500,
    last_game_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_username_format CHECK (username ~ '^[A-Za-z0-9_]{3,20}$')
);
CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));

CREATE TABLE invite_codes (
    id          BIGSERIAL PRIMARY KEY,
    code_hash   VARCHAR(64) NOT NULL UNIQUE,
    created_by  BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX ix_invite_codes_created_by ON invite_codes (created_by);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE tournaments (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(80) NOT NULL,
    format             VARCHAR(20) NOT NULL CHECK (format IN ('ROUND_ROBIN', 'SINGLE_ELIMINATION')),
    status             VARCHAR(15) NOT NULL DEFAULT 'REGISTRATION'
                       CHECK (status IN ('REGISTRATION', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    double_round_robin BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by         BIGINT      NOT NULL REFERENCES users (id),
    winner_id          BIGINT      REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    version            BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_tournaments_status ON tournaments (status);

CREATE TABLE tournament_participants (
    tournament_id BIGINT      NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    seed          INTEGER,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tournament_id, user_id)
);
CREATE INDEX ix_tournament_participants_user ON tournament_participants (user_id);

CREATE TABLE tournament_matches (
    id            BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT      NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    round_number  INTEGER     NOT NULL CHECK (round_number >= 1),
    board_number  INTEGER     NOT NULL CHECK (board_number >= 0),
    white_id      BIGINT      REFERENCES users (id),
    black_id      BIGINT      REFERENCES users (id),
    status        VARCHAR(10) NOT NULL CHECK (status IN ('WAITING', 'READY', 'COMPLETED', 'BYE')),
    result        VARCHAR(10) CHECK (result IN ('WHITE_WIN', 'BLACK_WIN', 'DRAW')),
    winner_id     BIGINT      REFERENCES users (id),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ux_match_slot UNIQUE (tournament_id, round_number, board_number),
    CONSTRAINT ck_match_players_differ CHECK (white_id IS NULL OR black_id IS NULL OR white_id <> black_id)
);
CREATE INDEX ix_tournament_matches_white ON tournament_matches (white_id);
CREATE INDEX ix_tournament_matches_black ON tournament_matches (black_id);

CREATE TABLE games (
    id                  BIGSERIAL PRIMARY KEY,
    white_id            BIGINT       NOT NULL REFERENCES users (id),
    black_id            BIGINT       NOT NULL REFERENCES users (id),
    result              VARCHAR(10)  NOT NULL CHECK (result IN ('WHITE_WIN', 'BLACK_WIN', 'DRAW')),
    status              VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'CONFIRMED', 'DISPUTED', 'VOIDED')),
    played_at           TIMESTAMPTZ  NOT NULL,
    reported_by         BIGINT       NOT NULL REFERENCES users (id),
    confirmed_by        BIGINT       REFERENCES users (id),
    confirmed_at        TIMESTAMPTZ,
    dispute_reason      VARCHAR(500),
    opening             VARCHAR(80),
    eco                 VARCHAR(3),
    notes               VARCHAR(1000),
    moves               TEXT,
    tournament_match_id BIGINT       REFERENCES tournament_matches (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_games_players_differ CHECK (white_id <> black_id),
    CONSTRAINT ck_games_moves_length CHECK (moves IS NULL OR length(moves) <= 20000),
    CONSTRAINT ck_games_eco CHECK (eco IS NULL OR eco ~ '^[A-E][0-9]{2}$')
);
CREATE INDEX ix_games_white_played ON games (white_id, played_at);
CREATE INDEX ix_games_black_played ON games (black_id, played_at);
CREATE INDEX ix_games_status_created ON games (status, created_at);
CREATE INDEX ix_games_played ON games (played_at DESC, id DESC);
CREATE INDEX ix_games_match ON games (tournament_match_id);
-- At most one open (pending/disputed) game per tournament match
CREATE UNIQUE INDEX ux_games_open_per_match ON games (tournament_match_id)
    WHERE tournament_match_id IS NOT NULL AND status IN ('PENDING', 'DISPUTED');

CREATE TABLE rating_history (
    id               BIGSERIAL PRIMARY KEY,
    game_id          BIGINT           NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    user_id          BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating_before    DOUBLE PRECISION NOT NULL,
    rating_after     DOUBLE PRECISION NOT NULL,
    rd_before        DOUBLE PRECISION NOT NULL,
    rd_after         DOUBLE PRECISION NOT NULL,
    volatility_after DOUBLE PRECISION NOT NULL,
    played_at        TIMESTAMPTZ      NOT NULL,
    CONSTRAINT ux_rating_history_game_user UNIQUE (game_id, user_id)
);
CREATE INDEX ix_rating_history_user_played ON rating_history (user_id, played_at);
