-- ingestion-service owns schema ingestion (Q-14: shared cluster, schema per service)
CREATE SCHEMA IF NOT EXISTS ingestion;

CREATE TABLE ingestion.corpus (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    seed_urls       JSONB NOT NULL,
    policy          JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'paused', 'archived')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_corpus_name UNIQUE (name)
);

CREATE TABLE ingestion.crawl_run (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_id       UUID NOT NULL REFERENCES ingestion.corpus (id) ON DELETE CASCADE,
    triggered_by    TEXT,
    status          TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'running', 'completed', 'failed', 'cancelled')),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    stats           JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_crawl_run_corpus ON ingestion.crawl_run (corpus_id);
CREATE INDEX idx_crawl_run_status ON ingestion.crawl_run (status);

CREATE TABLE ingestion.url_frontier (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    crawl_run_id    UUID NOT NULL REFERENCES ingestion.crawl_run (id) ON DELETE CASCADE,
    url             TEXT NOT NULL,
    url_hash        TEXT NOT NULL,
    depth           INT NOT NULL DEFAULT 0,
    parent_url      TEXT,
    status          TEXT NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'fetching', 'done', 'skipped', 'failed')),
    attempt_count   INT NOT NULL DEFAULT 0,
    last_error      TEXT,
    discovered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_frontier_run_url UNIQUE (crawl_run_id, url_hash)
);

CREATE INDEX idx_frontier_run_status ON ingestion.url_frontier (crawl_run_id, status);

CREATE TABLE ingestion.document (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_id               UUID NOT NULL REFERENCES ingestion.corpus (id) ON DELETE CASCADE,
    canonical_url           TEXT NOT NULL,
    url_hash                TEXT NOT NULL,
    title                   TEXT,
    language                TEXT,
    content_text            TEXT,
    content_hash            TEXT,
    raw_storage_key         TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    fetch_status            TEXT NOT NULL DEFAULT 'pending'
        CHECK (fetch_status IN ('pending', 'fetched', 'parsed', 'failed', 'skipped')),
    http_status             INT,
    fetched_at              TIMESTAMPTZ,
    supersedes_document_id  UUID REFERENCES ingestion.document (id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_corpus_url UNIQUE (corpus_id, url_hash)
);

CREATE INDEX idx_document_corpus ON ingestion.document (corpus_id);

CREATE TABLE ingestion.chunk (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES ingestion.document (id) ON DELETE CASCADE,
    corpus_id       UUID NOT NULL REFERENCES ingestion.corpus (id) ON DELETE CASCADE,
    sequence_no     INT NOT NULL,
    text            TEXT NOT NULL,
    text_hash       TEXT NOT NULL,
    token_count     INT,
    chunk_strategy  TEXT,
    embedding_model TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chunk_document_seq UNIQUE (document_id, sequence_no)
);

CREATE INDEX idx_chunk_corpus ON ingestion.chunk (corpus_id);

CREATE TABLE ingestion.vector_index (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id        UUID NOT NULL REFERENCES ingestion.chunk (id) ON DELETE CASCADE,
    collection_name TEXT NOT NULL,
    point_id        TEXT NOT NULL,
    index_version   TEXT NOT NULL DEFAULT 'v1',
    indexed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vector_index_chunk_collection_version
        UNIQUE (chunk_id, collection_name, index_version)
);

CREATE INDEX idx_vector_index_collection ON ingestion.vector_index (collection_name);
