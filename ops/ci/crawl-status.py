#!/usr/bin/env python3
import json
import os
import sys

import psycopg2

conn = psycopg2.connect(
    host=os.environ.get("PGHOST", "127.0.0.1"),
    port=int(os.environ.get("PGPORT", "5432")),
    dbname=os.environ.get("PGDATABASE", "geostat"),
    user=os.environ.get("PGUSER", "geostat"),
    password=os.environ.get("PGPASSWORD", "geostat-dev-change-me"),
)
cur = conn.cursor()
cur.execute("SELECT count(*) FROM ingestion.document")
docs = cur.fetchone()[0]
cur.execute(
    "SELECT status, count(*) FROM ingestion.url_frontier GROUP BY status ORDER BY 1"
)
frontier = dict(cur.fetchall())
cur.execute(
    """
    SELECT status, count(*) FROM ingestion.crawl_run
    GROUP BY status ORDER BY 1
    """
)
runs = dict(cur.fetchall())
cur.execute(
    """
    SELECT id, status, stats, finished_at
    FROM ingestion.crawl_run ORDER BY created_at DESC LIMIT 1
    """
)
latest = cur.fetchone()
cur.execute("SELECT policy FROM ingestion.corpus WHERE name='geostat-portal'")
policy = cur.fetchone()[0]
conn.close()
print(json.dumps({"docs": docs, "frontier": frontier, "runs": runs, "latest": latest, "policy": policy}, default=str))
