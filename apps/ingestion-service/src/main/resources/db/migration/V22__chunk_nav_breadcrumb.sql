ALTER TABLE ingestion.chunk
    ADD COLUMN IF NOT EXISTS nav_breadcrumb TEXT;

COMMENT ON COLUMN ingestion.chunk.nav_breadcrumb IS
    'Navigation breadcrumb copied from parent document at chunk insert time.';

UPDATE ingestion.chunk c
SET nav_breadcrumb = d.nav_breadcrumb
FROM ingestion.document d
WHERE c.document_id = d.id
  AND c.nav_breadcrumb IS NULL
  AND d.nav_breadcrumb IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chunk_nav_breadcrumb
    ON ingestion.chunk (corpus_id, nav_breadcrumb)
    WHERE nav_breadcrumb IS NOT NULL;
