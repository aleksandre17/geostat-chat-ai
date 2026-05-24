-- RAG-L08 prod eval set expansion (regression beyond 4 golden smoke queries)
INSERT INTO ingestion.evaluation_query (corpus_name, locale, query_text, expect_url, min_chunks)
SELECT v.corpus_name, v.locale, v.query_text, v.expect_url, v.min_chunks
FROM (VALUES
    ('geostat-portal', 'ka', 'დასაქმება', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'მშპ', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'ექსპორტი', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'მოსახლეობა', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'საგარეო ვაჭრობა', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'ფასების ინდექსი', 'geostat.ge', 1),
    ('geostat-portal', 'ka', 'ტურიზმი', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'employment', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'gdp', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'export', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'population', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'external trade', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'price index', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'tourism', 'geostat.ge', 1),
    ('geostat-portal', 'en', 'national accounts', 'geostat.ge', 1)
) AS v(corpus_name, locale, query_text, expect_url, min_chunks)
WHERE NOT EXISTS (
    SELECT 1 FROM ingestion.evaluation_query e
    WHERE e.query_text = v.query_text AND e.locale = v.locale
);
