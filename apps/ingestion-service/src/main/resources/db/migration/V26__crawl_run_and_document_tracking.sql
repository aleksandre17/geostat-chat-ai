-- V20 (Plan 10): incremental crawl + stale URL detection — columns not yet in schema
-- crawl_run, http_status, original_url, published_at, fetch_status='stale' already exist (V1/V20/V23)

-- raw_html_hash — incremental crawl optimization (skip re-parse when unchanged)
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS raw_html_hash CHAR(64);

COMMENT ON COLUMN ingestion.document.raw_html_hash IS
    'SHA-256 of raw fetched HTML (before any parsing).
     Used by CrawlPipeline: if hash unchanged on re-crawl → skip extraction.
     Recomputed on every fetch; changes = content changed → full re-pipeline.';

-- last_seen_at — stale URL detection
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ DEFAULT now();

COMMENT ON COLUMN ingestion.document.last_seen_at IS
    'Timestamp of last successful crawl for this URL.
     Updated on every successful HTTP 200 fetch.
     Stale detection: last_seen_at < (current crawl_run.started_at - grace_period)
     → mark fetch_status=stale → exclude from MV.';

UPDATE ingestion.document SET last_seen_at = updated_at WHERE last_seen_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_last_seen
    ON ingestion.document (corpus_id, last_seen_at);

CREATE INDEX IF NOT EXISTS idx_crawl_run_corpus_status
    ON ingestion.crawl_run (corpus_id, status);
