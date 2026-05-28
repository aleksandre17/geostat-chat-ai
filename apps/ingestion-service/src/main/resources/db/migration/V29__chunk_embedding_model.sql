-- V29: embedding model version tracking for incremental re-embedding on model upgrade

ALTER TABLE ingestion.chunk
  ADD COLUMN IF NOT EXISTS embedding_model TEXT;

COMMENT ON COLUMN ingestion.chunk.embedding_model IS
  'Identifier of the embedding model used (e.g. "gemini/text-embedding-004").
   Set at embed time by the embedding pipeline, NOT at chunk creation time.
   NULL = not yet embedded or model unknown.
   Used for: incremental re-embedding when upgrading to a new model.
   Query: SELECT id FROM chunk WHERE embedding_model != :newModel → re-embed only stale chunks.';

-- Backfill: mark all currently-embedded chunks with the known current model
UPDATE ingestion.chunk
SET embedding_model = 'gemini/text-embedding-004'
WHERE embedding_status = 'embedded'
  AND embedding_model IS NULL;
