#!/usr/bin/env python3
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
    SELECT column_name FROM information_schema.columns
    WHERE table_schema='ingestion' AND table_name='document'
      AND column_name='display_description'
    """
)
print("column:", cur.fetchone())
cur.execute("SELECT count(*) FROM ingestion.document WHERE display_description IS NOT NULL")
print("with_desc:", cur.fetchone()[0])
cur.execute("SELECT count(*) FROM ingestion.document")
print("docs:", cur.fetchone()[0])
conn.close()
