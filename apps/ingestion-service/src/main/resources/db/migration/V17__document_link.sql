-- Document link graph: stores parent→child URL relationships discovered during crawl.
-- Decouples PageRank computation (enrichment layer) from url_frontier (crawl layer).
-- Populated by CrawlRunStore. Read by JGraphTPageRankAuthorityDeriver.

CREATE TABLE IF NOT EXISTS ingestion.document_link (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    corpus_id       UUID         NOT NULL REFERENCES ingestion.corpus(id)   ON DELETE CASCADE,
    source_doc_id   UUID         NOT NULL REFERENCES ingestion.document(id)  ON DELETE CASCADE,
    target_url      TEXT         NOT NULL,
    target_doc_id   UUID                  REFERENCES ingestion.document(id)  ON DELETE SET NULL,
    crawl_run_id    UUID                  REFERENCES ingestion.crawl_run(id) ON DELETE SET NULL,
    discovered_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_document_link PRIMARY KEY (id)
);

CREATE INDEX idx_document_link_source ON ingestion.document_link(source_doc_id);

CREATE INDEX idx_document_link_target ON ingestion.document_link(target_doc_id)
    WHERE target_doc_id IS NOT NULL;

CREATE UNIQUE INDEX idx_document_link_dedup
    ON ingestion.document_link(source_doc_id, target_url);

-- Move embedding_model ownership to vector_index (Phase C)
ALTER TABLE ingestion.vector_index
    ADD COLUMN IF NOT EXISTS embedding_model TEXT;

UPDATE ingestion.vector_index vi
SET embedding_model = c.embedding_model
FROM ingestion.chunk c
WHERE c.id = vi.chunk_id
  AND c.embedding_model IS NOT NULL;

-- Drop orphan table (Phase C): no Java references confirmed
DROP TABLE IF EXISTS ingestion.query_intent_cache;

-- One-time seed: populate document_link from existing url_frontier data
INSERT INTO ingestion.document_link (corpus_id, source_doc_id, target_url, target_doc_id, crawl_run_id)
SELECT DISTINCT
    d_source.corpus_id,
    d_source.id            AS source_doc_id,
    uf.url                 AS target_url,
    d_target.id            AS target_doc_id,
    uf.crawl_run_id
FROM ingestion.url_frontier uf
JOIN ingestion.document d_source
    ON d_source.canonical_url = uf.parent_url
    AND d_source.corpus_id = (SELECT corpus_id FROM ingestion.crawl_run WHERE id = uf.crawl_run_id)
LEFT JOIN ingestion.document d_target
    ON d_target.canonical_url = uf.url
    AND d_target.corpus_id = d_source.corpus_id
WHERE uf.parent_url IS NOT NULL
ON CONFLICT (source_doc_id, target_url) DO NOTHING;
