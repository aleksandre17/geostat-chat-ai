-- V20: redirect audit (original_url) + news date (published_at)

ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS original_url TEXT;

COMMENT ON COLUMN ingestion.document.original_url IS
    'Pre-redirect URL if canonical_url was updated after following redirect. NULL if no redirect occurred.';

ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

COMMENT ON COLUMN ingestion.document.published_at IS
    'Publication date extracted from news article metadata (JSON-LD, time element, OpenGraph).';

CREATE INDEX IF NOT EXISTS idx_document_published_at
    ON ingestion.document (corpus_id, published_at)
    WHERE published_at IS NOT NULL;
