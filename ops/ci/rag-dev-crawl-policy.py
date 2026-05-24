"""Hybrid dev: bounded dual-locale crawl for rag-locale-pipeline -FullRecrawl."""
import json
import os

import psycopg2

max_pages = int(os.environ.get("RAG_DEV_MAX_PAGES", "25"))
max_depth = int(os.environ.get("RAG_DEV_MAX_DEPTH", "4"))

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
    RETURNING name, policy
    """,
    (
        json.dumps(
            {
                "maxPagesPerRun": max_pages,
                "maxDepth": max_depth,
                "crawlMode": "bounded",
                "autoContinue": False,
            }
        ),
    ),
)
row = cur.fetchone()
conn.commit()
print(f"policy maxPages={row[1].get('maxPagesPerRun')} maxDepth={row[1].get('maxDepth')}")
cur.close()
conn.close()
