-- B-26: prod full-site crawl — single seed + link discovery until frontier exhausted.
UPDATE ingestion.corpus
SET policy = policy || '{
  "crawlMode": "full-site",
  "autoContinue": true,
  "maxDepth": 12,
  "maxPagesPerRun": 0
}'::jsonb
WHERE name = 'geostat-portal';
