-- RAG freshness: HTTP validators for incremental re-fetch
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS http_etag TEXT,
    ADD COLUMN IF NOT EXISTS last_modified TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_document_fetched_at ON ingestion.document (corpus_id, fetched_at DESC NULLS LAST);
