-- Browsers that want push notifications ("Ola logged a game against you – confirm?"). One row per browser
-- (endpoint); the keys are the browser's own public encryption keys, not secrets of ours.
CREATE TABLE push_subscriptions (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint   VARCHAR(1000) NOT NULL UNIQUE,
    p256dh     VARCHAR(200) NOT NULL,
    auth       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX push_subscriptions_user ON push_subscriptions (user_id);
