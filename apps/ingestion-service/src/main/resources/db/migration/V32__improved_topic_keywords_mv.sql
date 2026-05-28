-- V32 — Replace mv_topic_keywords with multi-source TF-IDF variant.
-- Includes both YAKE (document-level) and Gemini (cluster-level) keywords.
-- Score = doc_frequency / across_clusters (discriminative power).
-- Tokens shorter than 3 chars are excluded to eliminate Georgian particles.

DROP MATERIALIZED VIEW IF EXISTS ingestion.mv_topic_keywords CASCADE;

CREATE MATERIALIZED VIEW ingestion.mv_topic_keywords AS
WITH
-- Source 1: YAKE keywords from individual documents (frequency-based)
yake_kw AS (
    SELECT d.topic_cluster_id,
           d.language,
           lower(kw) AS kw
    FROM   ingestion.document d
    JOIN   ingestion.topic_cluster tc ON tc.id = d.topic_cluster_id AND tc.approved = true,
           unnest(d.keywords) AS kw
    WHERE  d.topic_cluster_id IS NOT NULL
      AND  length(kw) >= 3
),

-- Source 2: Gemini semantic keywords from cluster (stored in topic_cluster.keywords)
-- Gemini keywords are language-agnostic — include for both ka and en
gemini_kw AS (
    SELECT tc.id  AS topic_cluster_id,
           'ka'   AS language,
           lower(kw) AS kw
    FROM   ingestion.topic_cluster tc, unnest(tc.keywords) AS kw
    WHERE  tc.approved = true AND length(kw) >= 3
    UNION ALL
    SELECT tc.id, 'en', lower(kw)
    FROM   ingestion.topic_cluster tc, unnest(tc.keywords) AS kw
    WHERE  tc.approved = true AND length(kw) >= 3
),

-- Combine: Gemini keywords added twice → higher doc_frequency in aggregation
-- This is the "Gemini boost" without a separate weight column
all_kw AS (
    SELECT * FROM yake_kw
    UNION ALL
    SELECT * FROM gemini_kw
    UNION ALL
    SELECT * FROM gemini_kw   -- second copy = 2x weight in COUNT
),

-- Aggregate: how many times does this keyword appear per cluster?
per_cluster AS (
    SELECT topic_cluster_id, language, kw,
           COUNT(*) AS doc_frequency
    FROM   all_kw
    GROUP BY topic_cluster_id, language, kw
),

-- IDF denominator: how many clusters share this keyword?
per_keyword_scope AS (
    SELECT language, kw,
           COUNT(DISTINCT topic_cluster_id) AS across_clusters
    FROM   per_cluster
    GROUP BY language, kw
)

SELECT
    pc.topic_cluster_id,
    pc.language,
    pc.kw                                                                    AS keyword,
    pc.doc_frequency,
    pks.across_clusters,
    -- TF-IDF proxy: intra-cluster frequency vs cross-cluster spread
    pc.doc_frequency::float / GREATEST(1.0, pks.across_clusters::float)     AS tfidf_score,
    ROW_NUMBER() OVER (
        PARTITION BY pc.topic_cluster_id, pc.language
        ORDER BY pc.doc_frequency::float / GREATEST(1.0, pks.across_clusters::float) DESC
    )                                                                        AS rank
FROM per_cluster pc
JOIN per_keyword_scope pks ON pks.language = pc.language AND pks.kw = pc.kw;

-- Index for fast lookup by cluster + language + rank
CREATE UNIQUE INDEX idx_mv_topic_keywords_unique
    ON ingestion.mv_topic_keywords (topic_cluster_id, language, rank);

-- Index for keyword-based search (the query path)
CREATE INDEX idx_mv_topic_keywords_kw
    ON ingestion.mv_topic_keywords (language, keyword, tfidf_score DESC);
