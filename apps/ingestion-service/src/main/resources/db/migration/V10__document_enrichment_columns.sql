-- RAG-U01 / spec V9 — per-document derived enrichment columns (Phase 8 Layer 2)

ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS summary_ka          TEXT,
    ADD COLUMN IF NOT EXISTS summary_en          TEXT,
    ADD COLUMN IF NOT EXISTS keywords            TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS entities            JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS locale_pair_doc_id  UUID REFERENCES ingestion.document(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS authority_score     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS page_kind           TEXT NOT NULL DEFAULT 'unknown'
        CHECK (page_kind IN ('portal', 'dataset', 'report', 'news', 'faq', 'navigation', 'unknown')),
    ADD COLUMN IF NOT EXISTS topic_cluster_id    UUID,
    ADD COLUMN IF NOT EXISTS score_boost         DOUBLE PRECISION NOT NULL DEFAULT 1.0
        CHECK (score_boost BETWEEN 0.5 AND 2.0),
    ADD COLUMN IF NOT EXISTS enrichment_version  INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_document_keywords_gin ON ingestion.document USING gin (keywords);
CREATE INDEX IF NOT EXISTS idx_document_entities_gin ON ingestion.document USING gin (entities);
CREATE INDEX IF NOT EXISTS idx_document_topic_cluster ON ingestion.document (topic_cluster_id);
CREATE INDEX IF NOT EXISTS idx_document_page_kind ON ingestion.document (page_kind);
CREATE INDEX IF NOT EXISTS idx_document_authority ON ingestion.document (authority_score DESC);
