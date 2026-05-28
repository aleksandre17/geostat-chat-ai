-- V36: convert content_hash from CHAR(64) to VARCHAR(64)
-- Hibernate 6 maps String fields to VARCHAR; CHAR(64)/bpchar fails schema validation.
-- The unique index idx_chunk_content_hash_corpus survives in-place type change.

ALTER TABLE ingestion.chunk
  ALTER COLUMN content_hash TYPE VARCHAR(64) USING content_hash::varchar;
