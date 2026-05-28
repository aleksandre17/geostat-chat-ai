-- Agriculture statistics portal (agriculture.geostat.ge) — independent second corpus.
-- Policy limits mirror ops/config/corpus/agriculture-ge-policy.yaml + headless fetch default.
-- renderMode is owned by ops/config/corpus/{name}-parse.yaml, not this policy JSON
INSERT INTO ingestion.corpus (id, name, seed_urls, policy, status)
VALUES (
    '7c9e6679-7425-40de-944b-e07fc1f90ae7',
    'agriculture-ge',
    '[
      "https://agriculture.geostat.ge/",
      "https://agriculture.geostat.ge/vegetation",
      "https://agriculture.geostat.ge/animal-husbandry",
      "https://agriculture.geostat.ge/aquaculture",
      "https://agriculture.geostat.ge/food-balance",
      "https://agriculture.geostat.ge/main-info"
    ]'::jsonb,
    '{
      "allowedHosts": ["agriculture.geostat.ge", "www.agriculture.geostat.ge"],
      "respectRobotsTxt": true,
      "maxDepth": 5,
      "maxPagesPerRun": 2000,
      "includePatterns": ["/"],
      "excludePatterns": [
        "/api/",
        "/static/",
        "/favicon",
        "/manifest.json",
        "/logo",
        ".png",
        ".jpg",
        ".svg",
        ".woff",
        "/login",
        "/admin",
        ".pdf",
        ".xlsx",
        ".xls",
        ".zip"
      ],
      "rateLimitMs": 700
    }'::jsonb,
    'active'
)
ON CONFLICT (name) DO NOTHING;
