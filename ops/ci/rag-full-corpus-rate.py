#!/usr/bin/env python3
"""Dev full crawl — faster rate limit (still polite)."""
import json
import os

import psycopg2

conn = psycopg2.connect(
    host=os.environ.get("PGHOST", "127.0.0.1"),
    port=int(os.environ.get("PGPORT", "5432")),
    dbname=os.environ.get("PGDATABASE", "geostat"),
    user=os.environ.get("PGUSER", "geostat"),
    password=os.environ.get("PGPASSWORD", "geostat-dev-change-me"),
)
cur = conn.cursor()
cur.execute(
    """
    UPDATE ingestion.corpus
    SET policy = policy || %s::jsonb
    WHERE name = 'geostat-portal'
    RETURNING policy->>'rateLimitMs' AS rate_ms
    """,
    (json.dumps({"rateLimitMs": 100}),),
)
print(f"rateLimitMs={cur.fetchone()[0]}")
conn.commit()
conn.close()
