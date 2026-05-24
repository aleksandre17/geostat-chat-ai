"""OPS-02 — restore production full-site crawl policy (no dev 25-page cap)."""
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
    RETURNING name, policy
    """,
    (
        json.dumps(
            {
                "crawlMode": "full-site",
                "autoContinue": True,
                "maxDepth": 12,
                "maxPagesPerRun": 0,
            }
        ),
    ),
)
row = cur.fetchone()
conn.commit()
print(f"full-site policy applied: crawlMode={row[1].get('crawlMode')} autoContinue={row[1].get('autoContinue')}")
cur.close()
conn.close()
