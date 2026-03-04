-- ============================================================
-- V1: Создание tracked_instruments и notifications
-- app_users уже создана AuthService — только ссылаемся на неё.
-- ============================================================

CREATE TABLE IF NOT EXISTS tracked_instruments (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    figi            VARCHAR(20) NOT NULL,
    instrument_name VARCHAR(255) NOT NULL,
    sell_price      NUMERIC(19, 4) NOT NULL,
    buy_price       NUMERIC(19, 4) NOT NULL,
    buy_alert_sent  BOOLEAN     NOT NULL DEFAULT FALSE,
    sell_alert_sent BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    app_user_id     UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tracked_instruments_app_user_id
    ON tracked_instruments(app_user_id);
CREATE INDEX IF NOT EXISTS idx_tracked_instruments_figi
    ON tracked_instruments(figi);

CREATE TABLE IF NOT EXISTS notifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id     UUID        REFERENCES app_users(id) ON DELETE SET NULL,
    user_id         BIGINT,
    chat_id         BIGINT,
    instrument_name VARCHAR(255) NOT NULL,
    figi            VARCHAR(20)  NOT NULL,
    alert_type      VARCHAR(10)  NOT NULL,
    current_price   NUMERIC(19, 4) NOT NULL,
    threshold       NUMERIC(19, 4) NOT NULL,
    message         VARCHAR(1024) NOT NULL,
    sent_to_telegram BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_app_user_id
    ON notifications(app_user_id);
