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
    SELECT table_schema, table_name FROM information_schema.tables
    WHERE table_name = 'flyway_schema_history'
    """
)
print("flyway tables:", cur.fetchall())
cur.execute(
    "SELECT version, description FROM ingestion.flyway_schema_history ORDER BY installed_rank DESC LIMIT 8"
)
for row in cur.fetchall():
    print(row)
conn.close()
