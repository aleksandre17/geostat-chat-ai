-- V28: content_text FTS index (DB-ARCH-01) + chunk cascade FK (DB-ARCH-02)

-- DB-ARCH-01: Full-text search index on document.content_text
-- Used by: CorpusReparseWorker queries, quality audit SQL, future FTS
-- NOT used for RAG (Qdrant handles vector retrieval)
CREATE INDEX IF NOT EXISTS idx_document_content_fts
  ON ingestion.document
  USING gin(to_tsvector('simple', COALESCE(content_text, '')))
  WHERE fetch_status = 'parsed';

COMMENT ON COLUMN ingestion.document.content_text IS
  'Full cleaned body text. Retained for: (1) CorpusReparseWorker re-chunking without re-fetch,
   (2) quality audit SQL queries, (3) FTS index.
   NOT used for RAG — Qdrant chunk vectors handle retrieval.
   Decision: storage cost (~45MB for 15k docs) justified by reparse and audit benefit.';

-- DB-ARCH-02: chunk → document CASCADE delete
-- Ensures: deleting a document removes all its chunks atomically
-- Prerequisite for: StaleDocumentCleanupJob, corpus reset, GDPR removal
ALTER TABLE ingestion.chunk
  DROP CONSTRAINT IF EXISTS chunk_document_id_fkey;

ALTER TABLE ingestion.chunk
  ADD CONSTRAINT chunk_document_id_fkey
  FOREIGN KEY (document_id)
  REFERENCES ingestion.document(id)
  ON DELETE CASCADE;

COMMENT ON CONSTRAINT chunk_document_id_fkey ON ingestion.chunk IS
  'CASCADE: deleting a document atomically removes all its chunks.
   VectorCleanupJob must delete Qdrant vectors BEFORE document deletion
   to prevent orphan vectors in the vector store.';

-- document → corpus: RESTRICT (prevent accidental corpus delete while documents exist)
ALTER TABLE ingestion.document
  DROP CONSTRAINT IF EXISTS document_corpus_id_fkey;

ALTER TABLE ingestion.document
  ADD CONSTRAINT document_corpus_id_fkey
  FOREIGN KEY (corpus_id)
  REFERENCES ingestion.corpus(id)
  ON DELETE RESTRICT;
