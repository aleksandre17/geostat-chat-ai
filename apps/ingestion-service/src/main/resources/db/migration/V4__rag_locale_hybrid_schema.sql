-- RAG-L01…L08: dual-locale corpus, chunk metadata, locale pairs, eval queries, keyword index

UPDATE ingestion.corpus
SET seed_urls = '["https://www.geostat.ge/ka", "https://www.geostat.ge/en"]'::jsonb
WHERE name = 'geostat-portal';

ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS section_path JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE ingestion.chunk
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_document_language ON ingestion.document (corpus_id, language);

CREATE INDEX IF NOT EXISTS idx_chunk_text_search ON ingestion.chunk
    USING gin (to_tsvector('simple', text));

CREATE TABLE IF NOT EXISTS ingestion.document_locale_pair (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_id       UUID NOT NULL REFERENCES ingestion.corpus (id) ON DELETE CASCADE,
    ka_document_id  UUID REFERENCES ingestion.document (id) ON DELETE SET NULL,
    en_document_id  UUID REFERENCES ingestion.document (id) ON DELETE SET NULL,
    ka_url          TEXT NOT NULL,
    en_url          TEXT NOT NULL,
    path_key        TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_locale_pair_path UNIQUE (corpus_id, path_key)
);

CREATE INDEX IF NOT EXISTS idx_locale_pair_corpus ON ingestion.document_locale_pair (corpus_id);

CREATE TABLE IF NOT EXISTS ingestion.evaluation_query (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_name     TEXT NOT NULL DEFAULT 'geostat-portal',
    locale          TEXT NOT NULL CHECK (locale IN ('ka', 'en')),
    query_text      TEXT NOT NULL,
    expect_url      TEXT,
    min_chunks      INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO ingestion.evaluation_query (corpus_name, locale, query_text, expect_url, min_chunks)
SELECT v.corpus_name, v.locale, v.query_text, v.expect_url, v.min_chunks
FROM (VALUES
    ('geostat-portal', 'ka', 'statistika', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'ინფლაცია', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'inflation', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'statistics', 'geostat.ge', 1)
) AS v(corpus_name, locale, query_text, expect_url, min_chunks)
WHERE NOT EXISTS (SELECT 1 FROM ingestion.evaluation_query e WHERE e.query_text = v.query_text AND e.locale = v.locale);
