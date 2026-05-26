-- RAG-U02 — Layer 3 catalog materialized views (spec §6 / V11)

CREATE MATERIALIZED VIEW ingestion.mv_portal_link AS
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
ORDER BY d.topic_cluster_id, d.language, d.authority_score DESC, d.fetched_at DESC;

CREATE UNIQUE INDEX idx_mv_portal_link_unique
    ON ingestion.mv_portal_link (topic_cluster_id, language);

CREATE MATERIALIZED VIEW ingestion.mv_specific_link AS
SELECT
    d.topic_cluster_id,
    d.language,
    d.page_kind,
    d.id              AS document_id,
    d.canonical_url,
    d.title,
    COALESCE(d.summary_ka, d.summary_en) AS summary,
    d.authority_score,
    ROW_NUMBER() OVER (
        PARTITION BY d.topic_cluster_id, d.language, d.page_kind
        ORDER BY d.authority_score DESC, d.fetched_at DESC
    ) AS rank_in_kind
FROM ingestion.document d
JOIN ingestion.topic_cluster tc
  ON tc.id = d.topic_cluster_id AND tc.approved = true
WHERE d.fetch_status = 'parsed'
  AND d.topic_cluster_id IS NOT NULL
  AND d.page_kind IN ('dataset', 'report', 'news', 'faq')
  AND NOT EXISTS (
      SELECT 1 FROM ingestion.curation_override co
      WHERE co.url_hash = d.url_hash AND co.action = 'exclude'
        AND (co.expires_at IS NULL OR co.expires_at > now())
  );

CREATE UNIQUE INDEX idx_mv_specific_link_unique
    ON ingestion.mv_specific_link (topic_cluster_id, language, page_kind, rank_in_kind);

CREATE INDEX idx_mv_specific_link_lookup
    ON ingestion.mv_specific_link (topic_cluster_id, language, page_kind, rank_in_kind);

CREATE MATERIALIZED VIEW ingestion.mv_topic_keywords AS
WITH unnested AS (
    SELECT
        d.topic_cluster_id,
        d.language,
        unnest(d.keywords) AS kw
    FROM ingestion.document d
    JOIN ingestion.topic_cluster tc
      ON tc.id = d.topic_cluster_id AND tc.approved = true
    WHERE d.topic_cluster_id IS NOT NULL
)
SELECT
    topic_cluster_id,
    language,
    kw          AS keyword,
    COUNT(*)    AS doc_frequency,
    ROW_NUMBER() OVER (
        PARTITION BY topic_cluster_id, language
        ORDER BY COUNT(*) DESC
    ) AS rank
FROM unnested
GROUP BY topic_cluster_id, language, kw;

CREATE UNIQUE INDEX idx_mv_topic_keywords_unique
    ON ingestion.mv_topic_keywords (topic_cluster_id, language, rank);

CREATE INDEX idx_mv_topic_keywords_lookup
    ON ingestion.mv_topic_keywords (topic_cluster_id, language, rank);
