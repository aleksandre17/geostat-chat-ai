-- V38: relax chunk content_hash uniqueness from corpus-wide to document-scoped.
--
-- The UNIQUE constraint idx_chunk_content_hash_corpus was corpus-wide, which
-- blocks re-crawl inserts when boilerplate chunks (same hash) already exist in
-- another document.  Per-document dedup is already enforced by uq_chunk_document_seq
-- (document_id, sequence_no).  We keep a non-unique index for query performance.

DROP INDEX IF EXISTS ingestion.idx_chunk_content_hash_corpus;

CREATE INDEX IF NOT EXISTS idx_chunk_content_hash_corpus
    ON ingestion.chunk (corpus_id, content_hash)
    WHERE content_hash IS NOT NULL;
