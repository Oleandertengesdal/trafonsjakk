-- Playing online: challenges, games with a server-kept clock, and a separate online rating.
-- A rated online game that ends with a result becomes an ordinary, already confirmed game
-- (games.mode = 'ONLINE'), so it shows up everywhere games do; only its rating is separate.

-- ---- games and ratings ------------------------------------------------------------------------------------

ALTER TABLE games ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'OTB' CHECK (mode IN ('OTB', 'ONLINE'));
-- "10+5" or "3d" for online games; over-the-board games are played without a clock
ALTER TABLE games ADD COLUMN time_control VARCHAR(12);

-- Which rating a history row belongs to. Each game's rows are in its own pool (games.mode).
ALTER TABLE rating_history ADD COLUMN pool VARCHAR(10) NOT NULL DEFAULT 'OTB' CHECK (pool IN ('OTB', 'ONLINE'));
CREATE INDEX ix_rating_history_user_pool_played ON rating_history (user_id, pool, played_at);

-- The online rating: a cache rebuilt by RatingService, like the over-the-board columns.
ALTER TABLE users
    ADD COLUMN online_rating           DOUBLE PRECISION NOT NULL DEFAULT 1500,
    ADD COLUMN online_rating_deviation DOUBLE PRECISION NOT NULL DEFAULT 350,
    ADD COLUMN online_volatility       DOUBLE PRECISION NOT NULL DEFAULT 0.06,
    ADD COLUMN online_rated_games      INTEGER          NOT NULL DEFAULT 0,
    ADD COLUMN online_peak_rating      DOUBLE PRECISION NOT NULL DEFAULT 1500,
    ADD COLUMN online_last_game_at     TIMESTAMPTZ;

-- ---- challenges -------------------------------------------------------------------------------------------

CREATE TABLE online_challenges (
    id                BIGSERIAL PRIMARY KEY,
    created_by        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- null: an open challenge anyone can take
    opponent_id       BIGINT      REFERENCES users (id) ON DELETE CASCADE,
    -- the colour the creator plays
    colour            VARCHAR(6)  NOT NULL CHECK (colour IN ('WHITE', 'BLACK', 'RANDOM')),
    rated             BOOLEAN     NOT NULL,
    kind              VARCHAR(5)  NOT NULL CHECK (kind IN ('LIVE', 'DAILY')),
    initial_seconds   INTEGER     NOT NULL DEFAULT 0,
    increment_seconds INTEGER     NOT NULL DEFAULT 0,
    days_per_move     INTEGER     NOT NULL DEFAULT 0,
    status            VARCHAR(9)  NOT NULL DEFAULT 'OPEN'
                      CHECK (status IN ('OPEN', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    game_id           BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_online_challenges_players CHECK (opponent_id IS NULL OR opponent_id <> created_by),
    CONSTRAINT ck_online_challenges_clock CHECK (
        (kind = 'LIVE' AND initial_seconds BETWEEN 60 AND 10800 AND increment_seconds BETWEEN 0 AND 60 AND days_per_move = 0)
        OR (kind = 'DAILY' AND days_per_move BETWEEN 1 AND 14 AND initial_seconds = 0 AND increment_seconds = 0))
);
CREATE INDEX ix_online_challenges_open ON online_challenges (expires_at) WHERE status = 'OPEN';

-- ---- games being played -----------------------------------------------------------------------------------

CREATE TABLE online_games (
    id                BIGSERIAL PRIMARY KEY,
    white_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    black_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    challenge_id      BIGINT      REFERENCES online_challenges (id) ON DELETE SET NULL,
    created_by        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rated             BOOLEAN     NOT NULL,
    kind              VARCHAR(5)  NOT NULL CHECK (kind IN ('LIVE', 'DAILY')),
    initial_seconds   INTEGER     NOT NULL DEFAULT 0,
    increment_seconds INTEGER     NOT NULL DEFAULT 0,
    days_per_move     INTEGER     NOT NULL DEFAULT 0,
    status            VARCHAR(8)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FINISHED', 'ABORTED')),
    result            VARCHAR(10) CHECK (result IN ('WHITE_WIN', 'BLACK_WIN', 'DRAW')),
    end_reason        VARCHAR(25),
    -- the score sheet, "1. e4 e5 2. Nf3", in the same form as games.moves; checked move by move
    moves             TEXT,
    plies             INTEGER     NOT NULL DEFAULT 0,
    last_move         VARCHAR(5),
    -- the clock: time left when the current turn started, and when it started
    white_ms          BIGINT      NOT NULL,
    black_ms          BIGINT      NOT NULL,
    turn_started_at   TIMESTAMPTZ NOT NULL,
    draw_offer_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    -- the confirmed game it became (rated games with a result)
    game_id           BIGINT      REFERENCES games (id) ON DELETE SET NULL,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_online_games_players CHECK (white_id <> black_id),
    CONSTRAINT ck_online_games_moves_length CHECK (moves IS NULL OR length(moves) <= 20000),
    CONSTRAINT ck_online_games_result CHECK ((status = 'FINISHED') = (result IS NOT NULL))
);
CREATE INDEX ix_online_games_active ON online_games (turn_started_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_online_games_white ON online_games (white_id, started_at);
CREATE INDEX ix_online_games_black ON online_games (black_id, started_at);
