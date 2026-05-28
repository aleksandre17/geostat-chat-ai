-- L-1-25: parsing fetch_status + chunk embedding_status for retry jobs
-- L-1-26: unique canonical_url per corpus (excluding stale documents)

ALTER TABLE ingestion.document DROP CONSTRAINT IF EXISTS document_fetch_status_check;
ALTER TABLE ingestion.document ADD CONSTRAINT document_fetch_status_check
  CHECK (fetch_status IN ('pending', 'fetched', 'parsing', 'parsed', 'failed', 'skipped', 'stale'));

ALTER TABLE ingestion.chunk
  ADD COLUMN IF NOT EXISTS embedding_status TEXT NOT NULL DEFAULT 'pending';

ALTER TABLE ingestion.chunk DROP CONSTRAINT IF EXISTS chunk_embedding_status_check;
ALTER TABLE ingestion.chunk ADD CONSTRAINT chunk_embedding_status_check
  CHECK (embedding_status IN ('pending', 'embedding', 'embedded', 'failed'));

UPDATE ingestion.chunk c
SET embedding_status = 'embedded'
WHERE embedding_status = 'pending'
  AND EXISTS (
    SELECT 1 FROM ingestion.vector_index vi WHERE vi.chunk_id = c.id
  );

CREATE INDEX IF NOT EXISTS idx_chunk_embedding_pending
  ON ingestion.chunk (embedding_status, created_at)
  WHERE embedding_status = 'pending';

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_canonical_corpus_unique
  ON ingestion.document (corpus_id, canonical_url)
  WHERE fetch_status != 'stale';
