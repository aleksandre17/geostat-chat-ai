#!/usr/bin/env python3
"""Fix boilerplate display_description rows using stored document title (RAG-L11 hotfix)."""
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
    UPDATE ingestion.document
    SET display_description = LEFT(TRIM(title), 240),
        meta_description = NULL,
        lead_text = NULL
    WHERE display_description ILIKE '%ვეგვერდ%'
       OR display_description ILIKE '%adapted version%'
       OR display_description ILIKE '%UNDP%'
       OR display_description ILIKE '%საჯარო სამართლის%'
    RETURNING canonical_url, LEFT(display_description, 120)
    """
)
rows = cur.fetchall()
conn.commit()
out = os.path.join(os.path.dirname(__file__), "fix-boilerplate-display-out.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump({"updated": len(rows), "samples": rows[:5]}, f, ensure_ascii=False, indent=2)
print(out)
conn.close()
