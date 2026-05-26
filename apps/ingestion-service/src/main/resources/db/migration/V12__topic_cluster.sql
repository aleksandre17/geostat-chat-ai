-- RAG-U01g — topic_cluster + curation overlay; wire document + eval FKs

CREATE TABLE IF NOT EXISTS ingestion.topic_cluster (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_id           UUID NOT NULL REFERENCES ingestion.corpus(id) ON DELETE CASCADE,
    label_ka            TEXT NOT NULL,
    label_en            TEXT NOT NULL,
    keywords            TEXT[] NOT NULL DEFAULT '{}',
    document_count      INT NOT NULL DEFAULT 0,
    centroid_summary    TEXT,
    centroid_embedding  REAL[],
    approved            BOOLEAN NOT NULL DEFAULT false,
    approved_by         TEXT,
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_topic_cluster_corpus_label_ka UNIQUE (corpus_id, label_ka)
);

CREATE INDEX IF NOT EXISTS idx_topic_cluster_corpus ON ingestion.topic_cluster (corpus_id);
CREATE INDEX IF NOT EXISTS idx_topic_cluster_approved ON ingestion.topic_cluster (corpus_id, approved);

ALTER TABLE ingestion.document
    DROP CONSTRAINT IF EXISTS fk_document_topic_cluster;

ALTER TABLE ingestion.document
    ADD CONSTRAINT fk_document_topic_cluster
    FOREIGN KEY (topic_cluster_id) REFERENCES ingestion.topic_cluster(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS ingestion.curation_override (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_hash    TEXT NOT NULL,
    action      TEXT NOT NULL
        CHECK (action IN ('boost','demote','exclude','pin_as_portal','rename_topic')),
    target      TEXT,
    payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason      TEXT NOT NULL,
    created_by  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    CONSTRAINT uq_override_url_action UNIQUE (url_hash, action)
);

CREATE INDEX IF NOT EXISTS idx_curation_override_url_hash ON ingestion.curation_override (url_hash);
-- Partial index cannot use now() (not IMMUTABLE); composite supports active/expired lookups at query time.
CREATE INDEX IF NOT EXISTS idx_curation_override_action_expires
    ON ingestion.curation_override (action, expires_at);

ALTER TABLE ingestion.evaluation_query
    DROP CONSTRAINT IF EXISTS fk_evaluation_query_expected_topic;

ALTER TABLE ingestion.evaluation_query
    ADD CONSTRAINT fk_evaluation_query_expected_topic
    FOREIGN KEY (expected_topic) REFERENCES ingestion.topic_cluster(id) ON DELETE SET NULL;
