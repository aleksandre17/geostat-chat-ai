-- CROSS-GAP-01: allow 'deleted' embedding_status for VectorCleanupJob audit trail
ALTER TABLE ingestion.chunk DROP CONSTRAINT IF EXISTS chunk_embedding_status_check;
ALTER TABLE ingestion.chunk ADD CONSTRAINT chunk_embedding_status_check
    CHECK (embedding_status IN ('pending', 'embedding', 'embedded', 'failed', 'deleted'));
