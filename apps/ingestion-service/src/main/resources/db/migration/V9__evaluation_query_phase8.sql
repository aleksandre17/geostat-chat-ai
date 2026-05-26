-- RAG-U12 — extend golden set metadata (Phase 8 eval harness)
-- expected_topic FK added in V11 when topic_cluster exists

ALTER TABLE ingestion.evaluation_query
    ADD COLUMN IF NOT EXISTS expected_intent   TEXT,
    ADD COLUMN IF NOT EXISTS expected_entities JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS expected_topic    UUID,
    ADD COLUMN IF NOT EXISTS difficulty        TEXT NOT NULL DEFAULT 'medium'
        CHECK (difficulty IN ('easy', 'medium', 'hard')),
    ADD COLUMN IF NOT EXISTS source            TEXT NOT NULL DEFAULT 'curated'
        CHECK (source IN ('curated', 'user_log', 'feedback'));

UPDATE ingestion.evaluation_query
SET source = 'curated',
    difficulty = 'medium'
WHERE source IS NULL OR difficulty IS NULL;

CREATE INDEX IF NOT EXISTS idx_evaluation_query_locale_active
    ON ingestion.evaluation_query (corpus_name, locale)
    WHERE active = true;
