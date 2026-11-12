-- "Månedens spiller": awards decided once a month has ended (plus a grace period for late confirmations).
-- One row per month and kind. user_id is NULL when the month was decided but nobody qualified.
-- Rows are written once and never updated, so late changes to old games don't move a decided award.
CREATE TABLE monthly_awards (
    month         DATE             NOT NULL,
    kind          VARCHAR(20)      NOT NULL CHECK (kind IN ('PLAYER_OF_MONTH', 'MOST_ACTIVE')),
    user_id       BIGINT           REFERENCES users (id) ON DELETE CASCADE,
    games         INT              NOT NULL DEFAULT 0,
    points        DOUBLE PRECISION NOT NULL DEFAULT 0,
    expected      DOUBLE PRECISION NOT NULL DEFAULT 0,
    rating_change DOUBLE PRECISION NOT NULL DEFAULT 0,
    decided_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (month, kind),
    CHECK (extract(day from month) = 1)
);

CREATE INDEX ix_monthly_awards_user ON monthly_awards (user_id);
