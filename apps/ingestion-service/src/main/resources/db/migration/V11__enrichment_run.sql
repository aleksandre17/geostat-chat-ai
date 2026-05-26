-- RAG-U01a/U01 — enrichment run log (spec V10 subset; topic_cluster/curation in later migration)

CREATE TABLE IF NOT EXISTS ingestion.enrichment_run (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES ingestion.document(id) ON DELETE CASCADE,
    deriver_kind    TEXT NOT NULL
        CHECK (deriver_kind IN (
            'summary', 'keywords', 'entities', 'locale_pair',
            'authority', 'page_kind', 'topic_assign', 'title_vector', 'summary_vector'
        )),
    status          TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'running', 'completed', 'failed', 'skipped')),
    model_version   TEXT NOT NULL,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    duration_ms     INT,
    cost_units      INT,
    error           TEXT,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_enrichment_doc_kind_version UNIQUE (document_id, deriver_kind, model_version)
);

CREATE INDEX IF NOT EXISTS idx_enrichment_run_doc ON ingestion.enrichment_run (document_id);
CREATE INDEX IF NOT EXISTS idx_enrichment_run_status ON ingestion.enrichment_run (status, deriver_kind);
