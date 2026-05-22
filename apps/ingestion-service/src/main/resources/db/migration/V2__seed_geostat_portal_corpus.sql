-- Default corpus for geostat.ge (agnostic model — other sites = additional corpus rows)
INSERT INTO ingestion.corpus (id, name, seed_urls, policy, status)
VALUES (
    'a0000000-0000-4000-8000-000000000001',
    'geostat-portal',
    '["https://www.geostat.ge/ka"]'::jsonb,
    '{
      "allowedHosts": ["www.geostat.ge", "geostat.ge"],
      "respectRobotsTxt": true,
      "maxDepth": null,
      "maxPagesPerRun": null,
      "includePatterns": [],
      "excludePatterns": ["/login", "/admin"],
      "fetchMode": "http",
      "rateLimitMs": 500
    }'::jsonb,
    'active'
)
ON CONFLICT (name) DO NOTHING;
