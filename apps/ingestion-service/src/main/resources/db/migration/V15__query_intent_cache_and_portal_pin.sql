-- RAG-U14 — query intent cache (spec §4 V12)
-- RAG-U05 — mv_portal_link respects pin_as_portal overrides (spec §7)

CREATE TABLE IF NOT EXISTS ingestion.query_intent_cache (
    query_hash    TEXT PRIMARY KEY,
    locale        TEXT NOT NULL,
    intent        TEXT NOT NULL
        CHECK (intent IN ('factual','lookup','compare','definition','latest','navigation','smalltalk')),
    entities      JSONB NOT NULL DEFAULT '[]'::jsonb,
    expansions    JSONB NOT NULL DEFAULT '[]'::jsonb,
    cached_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours')
);

CREATE INDEX IF NOT EXISTS idx_query_intent_cache_expires
    ON ingestion.query_intent_cache (expires_at);

DROP MATERIALIZED VIEW IF EXISTS ingestion.mv_portal_link;

CREATE MATERIALIZED VIEW ingestion.mv_portal_link AS
WITH pinned AS (
    SELECT DISTINCT ON (co.target::uuid, d.language)
        co.target::uuid AS topic_cluster_id,
        d.language,
        d.id              AS document_id,
        d.canonical_url,
        d.title,
        COALESCE(d.summary_ka, d.summary_en) AS summary,
        d.authority_score
    FROM ingestion.curation_override co
    JOIN ingestion.document d ON d.url_hash = co.url_hash
    JOIN ingestion.topic_cluster tc
      ON tc.id = co.target::uuid AND tc.approved = true
    WHERE co.action = 'pin_as_portal'
      AND co.target IS NOT NULL
      AND (co.expires_at IS NULL OR co.expires_at > now())
    ORDER BY co.target::uuid, d.language, co.created_at DESC
),
derived AS (
    SELECT DISTINCT ON (d.topic_cluster_id, d.language)
        d.topic_cluster_id,
        d.language,
        d.id              AS document_id,
        d.canonical_url,
        d.title,
        COALESCE(d.summary_ka, d.summary_en) AS summary,
        d.authority_score
    FROM ingestion.document d
    JOIN ingestion.topic_cluster tc
      ON tc.id = d.topic_cluster_id AND tc.approved = true
    WHERE d.fetch_status = 'parsed'
      AND d.topic_cluster_id IS NOT NULL
      AND d.page_kind = 'portal'
      AND NOT EXISTS (
          SELECT 1 FROM ingestion.curation_override co
          WHERE co.url_hash = d.url_hash AND co.action = 'exclude'
            AND (co.expires_at IS NULL OR co.expires_at > now())
      )
      AND NOT EXISTS (
          SELECT 1 FROM ingestion.curation_override pin
          WHERE pin.action = 'pin_as_portal'
            AND pin.target = d.topic_cluster_id::text
            AND (pin.expires_at IS NULL OR pin.expires_at > now())
      )
    ORDER BY d.topic_cluster_id, d.language, d.authority_score DESC, d.fetched_at DESC
)
SELECT topic_cluster_id, language, document_id, canonical_url, title, summary, authority_score
FROM pinned
UNION ALL
SELECT topic_cluster_id, language, document_id, canonical_url, title, summary, authority_score
FROM derived;

CREATE UNIQUE INDEX idx_mv_portal_link_unique
    ON ingestion.mv_portal_link (topic_cluster_id, language);
