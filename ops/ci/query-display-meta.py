#!/usr/bin/env python3
import json
import os
import psycopg2

out_path = os.path.join(os.path.dirname(__file__), "query-display-meta-out.json")

conn = psycopg2.connect(
    host=os.environ.get("PGHOST", "127.0.0.1"),
    port=int(os.environ.get("PGPORT", "5432")),
    dbname=os.environ.get("PGDATABASE", "geostat"),
    user=os.environ.get("PGUSER", "geostat"),
    password=os.environ.get("PGPASSWORD", "geostat-dev-change-me"),
)
cur = conn.cursor()

patterns = ["%inflatsia%", "%fasebis-statistika%", "%regions.geostat%"]
results = []
for p in patterns:
    cur.execute(
        """
        SELECT canonical_url, language,
               meta_description, lead_text, display_description
        FROM ingestion.document
        WHERE canonical_url LIKE %s
        LIMIT 3
        """,
        (p,),
    )
    block = {"pattern": p, "rows": []}
    for row in cur.fetchall():
        block["rows"].append(
            {
                "url": row[0],
                "lang": row[1],
                "meta": row[2],
                "lead": row[3],
                "display": row[4],
            }
        )
    results.append(block)

cur.execute(
    """
    SELECT canonical_url, display_description
    FROM ingestion.document
    WHERE display_description ILIKE '%ვეგვერდ%'
       OR display_description ILIKE '%adapt%'
       OR lead_text ILIKE '%ვეგვერდ%'
    LIMIT 8
    """
)
boilerplate = [{"url": row[0], "display": row[1]} for row in cur.fetchall()]
conn.close()

with open(out_path, "w", encoding="utf-8") as f:
    json.dump({"documents": results, "boilerplate": boilerplate}, f, ensure_ascii=False, indent=2)
print(out_path)
