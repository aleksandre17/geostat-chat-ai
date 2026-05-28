-- Query intent cache for chat-api (RAG-U14). Previously in ingestion schema; dropped in ingestion V17.
CREATE TABLE IF NOT EXISTS chat.query_intent_cache (
    query_hash    TEXT PRIMARY KEY,
    locale        TEXT NOT NULL,
    intent        TEXT NOT NULL,
    cached_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours')
);

CREATE INDEX IF NOT EXISTS idx_query_intent_cache_expires
    ON chat.query_intent_cache (expires_at);
