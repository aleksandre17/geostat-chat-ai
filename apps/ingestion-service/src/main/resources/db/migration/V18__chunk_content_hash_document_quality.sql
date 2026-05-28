-- V18: chunk dedup hash + document quality columns + MV quality filter

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. document quality tracking
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS quality_score VARCHAR(16)
    CHECK (quality_score IN ('good', 'degraded', 'skip', 'rejected')),
  ADD COLUMN IF NOT EXISTS validation_violations TEXT[];

COMMENT ON COLUMN ingestion.document.quality_score IS
  'good | degraded | skip | rejected. skip = valid HTML, zero statistical content.
   rejected = not persisted (value only in audit log).';

COMMENT ON COLUMN ingestion.document.validation_violations IS
  'Array of violation codes: {truncated_text, paragraph_duplicates_removed,
   language_null_fallback, content_too_short, ...}';

UPDATE ingestion.document SET quality_score = 'good' WHERE quality_score IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_quality
  ON ingestion.document (corpus_id, quality_score);

-- 2. chunk content-hash for dedup
ALTER TABLE ingestion.chunk
  ADD COLUMN IF NOT EXISTS content_hash CHAR(64);

COMMENT ON COLUMN ingestion.chunk.content_hash IS
  'SHA-256 hex of: strip(lowercase(collapse_whitespace(text))).
   Must match ChunkHasher.hash() normalization exactly.';

UPDATE ingestion.chunk
SET content_hash = encode(
    digest(regexp_replace(lower(trim(text)), '\s+', ' ', 'g'), 'sha256'),
    'hex')
WHERE content_hash IS NULL AND text IS NOT NULL;

-- Remove duplicate hashes before creating the unique index.
-- Keeps the row with the highest id (most recent) per (corpus_id, content_hash).
DELETE FROM ingestion.chunk
WHERE id NOT IN (
    SELECT DISTINCT ON (corpus_id, content_hash) id
    FROM ingestion.chunk
    ORDER BY corpus_id, content_hash, id DESC
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_chunk_content_hash_corpus
  ON ingestion.chunk (corpus_id, content_hash)
  WHERE content_hash IS NOT NULL;

-- 3. Rebuild mv_topic_summary with quality filter
DROP MATERIALIZED VIEW IF EXISTS ingestion.mv_topic_summary;
CREATE MATERIALIZED VIEW ingestion.mv_topic_summary AS
SELECT
  d.corpus_id,
  d.id          AS document_id,
  d.page_kind,
  d.language,
  d.canonical_url,
  d.title,
  d.lead_text,
  d.summary_ka,
  d.summary_en,
  d.quality_score,
  COUNT(c.id)   AS chunk_count
FROM  ingestion.document d
LEFT  JOIN ingestion.chunk c ON c.document_id = d.id
WHERE d.fetch_status   = 'parsed'
  AND d.quality_score IN ('good', 'degraded')
  AND d.page_kind      IS DISTINCT FROM 'portal'
  AND COALESCE(length(d.content_text), 0) >= 100
GROUP BY d.corpus_id, d.id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_topic_summary_doc
  ON ingestion.mv_topic_summary (corpus_id, document_id);
