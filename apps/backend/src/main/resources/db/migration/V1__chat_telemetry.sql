-- B-19 / chat telemetry — optional JDBC store (profile telemetry-db)
CREATE SCHEMA IF NOT EXISTS chat;

CREATE TABLE IF NOT EXISTS chat.turn (
    id              UUID PRIMARY KEY,
    session_id      TEXT NOT NULL,
    user_message    TEXT,
    prompt_version  INT NOT NULL DEFAULT 0,
    prompt_hash     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_turn_session ON chat.turn (session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS chat.retrieval_hit (
    turn_id         UUID NOT NULL REFERENCES chat.turn (id) ON DELETE CASCADE,
    document_id     TEXT,
    source_url      TEXT,
    score           DOUBLE PRECISION NOT NULL DEFAULT 0,
    PRIMARY KEY (turn_id, document_id)
);

CREATE TABLE IF NOT EXISTS chat.feedback (
    turn_id         UUID PRIMARY KEY REFERENCES chat.turn (id) ON DELETE CASCADE,
    session_id      TEXT,
    rating          TEXT NOT NULL CHECK (rating IN ('up', 'down')),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
