"""Temporary E2E helper: limit geostat-portal crawl for faster hybrid smoke."""
import json
import sys

import psycopg2

conn = psycopg2.connect(
    host="127.0.0.1",
    port=5432,
    dbname="geostat",
    user="geostat",
    password="geostat-dev-change-me",
)
cur = conn.cursor()
cur.execute(
    """
    UPDATE ingestion.corpus
    SET policy = policy || %s::jsonb
    WHERE name = 'geostat-portal'
    RETURNING name, policy
    """,
    (json.dumps({
        "maxPagesPerRun": 3,
        "maxDepth": 1,
        "crawlMode": "bounded",
        "autoContinue": False,
    }),),
)
row = cur.fetchone()
conn.commit()
print(row[0], row[1].get("maxPagesPerRun"), row[1].get("maxDepth"))
cur.close()
conn.close()
