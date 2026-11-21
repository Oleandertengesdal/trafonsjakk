-- Engine analysis of a game, saved by the first browser that reviews it, so everyone else gets it at once.
-- evals: JSON, one entry per position (the start, then after every move), as the website's PositionEval.
-- moves_hash: SHA-256 of the move text it belongs to; changing the moves deletes the analysis.
-- The accuracies are computed by the server from evals (the same formula as the website).
CREATE TABLE game_analyses (
    game_id        BIGINT           PRIMARY KEY REFERENCES games (id) ON DELETE CASCADE,
    moves_hash     CHAR(64)         NOT NULL,
    depth          SMALLINT         NOT NULL CHECK (depth BETWEEN 1 AND 99),
    plies          INT              NOT NULL CHECK (plies >= 1),
    evals          TEXT             NOT NULL,
    white_accuracy DOUBLE PRECISION NOT NULL CHECK (white_accuracy BETWEEN 0 AND 100),
    black_accuracy DOUBLE PRECISION NOT NULL CHECK (black_accuracy BETWEEN 0 AND 100),
    analysed_by    BIGINT           REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- "Most accurate player of the month": average accuracy over the month's analysed, confirmed games.
ALTER TABLE monthly_awards DROP CONSTRAINT monthly_awards_kind_check;
ALTER TABLE monthly_awards ADD CONSTRAINT monthly_awards_kind_check
    CHECK (kind IN ('PLAYER_OF_MONTH', 'MOST_ACTIVE', 'MOST_ACCURATE'));
ALTER TABLE monthly_awards ADD COLUMN accuracy DOUBLE PRECISION;
