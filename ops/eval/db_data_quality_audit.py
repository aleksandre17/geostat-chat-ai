#!/usr/bin/env python3
"""PostgreSQL data quality audit for ingestion tables."""

from __future__ import annotations

import re
import sys
from datetime import datetime, timezone
from pathlib import Path

import psycopg2
from psycopg2.extras import RealDictCursor

DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 5432,
    "dbname": "geostat",
    "user": "geostat",
    "password": "geostat-dev-change-me",
}

REPORT_PATH = Path(__file__).resolve().parent / "reports" / "2026-05-26-db-data-quality-audit.txt"

SENTENCE_END = re.compile(r'[.!?\"»]\s*$')
BOUNDARY_LENGTHS = (500, 1000, 2000, 5000)
GEORGIAN_RE = re.compile(r"[\u10A0-\u10FF]")
LATIN_RE = re.compile(r"[A-Za-z]")


class Report:
    def __init__(self) -> None:
        self._lines: list[str] = []

    def line(self, text: str = "") -> None:
        self._lines.append(text)
        print(text)

    def header(self, title: str) -> None:
        self.line(f"=== {title} ===")

    def subheader(self, title: str) -> None:
        self.line(f"--- {title} ---")

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(self._lines) + "\n", encoding="utf-8")


def first_alpha_char(text: str) -> str | None:
    for ch in text:
        if ch.isalpha():
            return ch
    return None


def ends_without_sentence_punctuation(text: str) -> bool:
    stripped = text.rstrip()
    if not stripped:
        return False
    return SENTENCE_END.search(stripped) is None


def truncation_examples(rows, url_key, text_key, check_fn, limit=10):
    out = []
    for row in rows:
        text = row.get(text_key) or ""
        if not text or not check_fn(text):
            continue
        url = row.get(url_key) or row.get("canonical_url") or str(row.get("id", ""))
        tail = text[-60:] if len(text) >= 60 else text
        out.append((url, tail))
        if len(out) >= limit:
            break
    return out


def audit_null_empty(cur, report):
    report.header("1. NULL / EMPTY FIELDS")

    report.subheader("ingestion.document")
    doc_checks = [
        ("title NULL or empty", "title IS NULL OR btrim(title) = ''"),
        ("canonical_url NULL or empty", "canonical_url IS NULL OR btrim(canonical_url) = ''"),
        ("summary_ka NULL or empty", "summary_ka IS NULL OR btrim(summary_ka) = ''"),
        ("summary_en NULL or empty", "summary_en IS NULL OR btrim(summary_en) = ''"),
        ("keywords empty array", "keywords IS NULL OR cardinality(keywords) = 0"),
        ("content_text NULL or empty", "content_text IS NULL OR btrim(content_text) = ''"),
        ("lead_text NULL or empty", "lead_text IS NULL OR btrim(lead_text) = ''"),
        ("meta_description NULL or empty", "meta_description IS NULL OR btrim(meta_description) = ''"),
        ("page_kind = 'unknown'", "page_kind = 'unknown'"),
    ]
    for label, where in doc_checks:
        cur.execute(f"SELECT COUNT(*) AS c FROM ingestion.document WHERE {where}")
        report.line(f"  {label}: {cur.fetchone()['c']}")

    report.subheader("ingestion.chunk")
    cur.execute(
        "SELECT COUNT(*) AS c FROM ingestion.chunk WHERE text IS NULL OR btrim(text) = ''"
    )
    report.line(f"  text NULL or empty: {cur.fetchone()['c']}")

    report.subheader("ingestion.topic_cluster")
    for col in ("label_ka", "label_en"):
        cur.execute(
            f"SELECT COUNT(*) AS c FROM ingestion.topic_cluster "
            f"WHERE {col} IS NULL OR btrim({col}) = ''"
        )
        report.line(f"  {col} NULL or empty: {cur.fetchone()['c']}")

    for table in ("ingestion.mv_portal_link", "ingestion.mv_specific_link"):
        report.subheader(table)
        for col in ("title", "summary", "canonical_url"):
            cur.execute(
                f"SELECT COUNT(*) AS c FROM {table} "
                f"WHERE {col} IS NULL OR btrim({col}) = ''"
            )
            report.line(f"  {col} NULL or empty: {cur.fetchone()['c']}")

    report.subheader("ingestion.mv_topic_keywords")
    report.line(
        "  (columns: topic_cluster_id, language, keyword, doc_frequency, rank)"
    )
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM ingestion.mv_topic_keywords
        WHERE keyword IS NULL OR btrim(keyword) = ''
        """
    )
    report.line(f"  keyword NULL or empty: {cur.fetchone()['c']}")


def fetch_document_sample(cur, limit=200):
    cur.execute(
        """
        SELECT id, canonical_url, content_text
        FROM ingestion.document
        TABLESAMPLE BERNOULLI (100)
        LIMIT %s
        """,
        (limit,),
    )
    rows = cur.fetchall()
    if len(rows) < limit:
        cur.execute(
            """
            SELECT id, canonical_url, content_text
            FROM ingestion.document
            ORDER BY random()
            LIMIT %s
            """,
            (limit,),
        )
        rows = cur.fetchall()
    return rows


def run_truncation_block(report, name, rows, url_key, text_key):
    report.subheader(name)
    n = len(rows)
    report.line(f"  Sample size: {n}")

    no_end = [
        r
        for r in rows
        if (r.get(text_key) or "") and ends_without_sentence_punctuation(r[text_key])
    ]
    report.line(f'  Ends without sentence punctuation (. ! ? " »): {len(no_end)} / {n}')
    for url, tail in truncation_examples(
        rows, url_key, text_key, ends_without_sentence_punctuation
    ):
        report.line(f"    url={url!r}")
        report.line(f"      ...{tail!r}")

    at_boundary = [r for r in rows if len(r.get(text_key) or "") in BOUNDARY_LENGTHS]
    report.line(f"  Length exactly at boundary {BOUNDARY_LENGTHS}: {len(at_boundary)} / {n}")
    shown = 0
    for row in at_boundary:
        text = row.get(text_key) or ""
        url = row.get(url_key) or row.get("canonical_url") or str(row.get("id", ""))
        report.line(f"    url={url!r} len={len(text)}")
        report.line(f"      ...{text[-60:]!r}")
        shown += 1
        if shown >= 10:
            break

    def lowercase_start(t):
        ch = first_alpha_char(t.lstrip())
        return ch is not None and ch.islower()

    lower_start = [r for r in rows if lowercase_start(r.get(text_key) or "")]
    report.line(f"  Starts with lowercase letter (mid-sentence): {len(lower_start)} / {n}")
    for url, tail in truncation_examples(rows, url_key, text_key, lowercase_start):
        report.line(f"    url={url!r}")
        report.line(f"      ...{tail!r}")


def audit_truncation(cur, report):
    report.header("2. TRUNCATION (random samples)")
    doc_rows = fetch_document_sample(cur, 200)
    cur.execute(
        """
        SELECT c.id, c.document_id, c.text, d.canonical_url
        FROM ingestion.chunk c
        LEFT JOIN ingestion.document d ON d.id = c.document_id
        ORDER BY random()
        LIMIT 200
        """
    )
    chunk_rows = cur.fetchall()
    run_truncation_block(
        report, "document.content_text (sample 200)", doc_rows, "canonical_url", "content_text"
    )
    run_truncation_block(
        report, "chunk.text (sample 200)", chunk_rows, "canonical_url", "text"
    )


def audit_duplicates(cur, report):
    report.header("3. DUPLICATES")

    report.subheader("document — duplicate canonical_url")
    cur.execute(
        """
        SELECT canonical_url, COUNT(*) AS cnt
        FROM ingestion.document
        WHERE canonical_url IS NOT NULL AND btrim(canonical_url) <> ''
        GROUP BY canonical_url
        HAVING COUNT(*) > 1
        ORDER BY cnt DESC
        LIMIT 20
        """
    )
    dup_urls = cur.fetchall()
    report.line(f"  Showing up to 20 duplicate canonical_url groups: {len(dup_urls)} rows returned")
    for row in dup_urls:
        report.line(f"    {row['canonical_url']!r}: {row['cnt']} rows")

    cur.execute(
        """
        SELECT COUNT(*) AS groups FROM (
            SELECT canonical_url FROM ingestion.document
            WHERE canonical_url IS NOT NULL AND btrim(canonical_url) <> ''
            GROUP BY canonical_url HAVING COUNT(*) > 1
        ) t
        """
    )
    report.line(f"  Total duplicate canonical_url groups: {cur.fetchone()['groups']}")

    report.subheader("document — duplicate (title, language)")
    cur.execute(
        """
        SELECT title, language, COUNT(*) AS cnt
        FROM ingestion.document
        WHERE title IS NOT NULL AND btrim(title) <> ''
        GROUP BY title, language
        HAVING COUNT(*) > 1
        ORDER BY cnt DESC
        LIMIT 20
        """
    )
    dup_titles = cur.fetchall()
    report.line(f"  Showing up to 20 duplicate (title, language) groups")
    for row in dup_titles:
        t = row["title"]
        preview = t[:80] + ("..." if len(t) > 80 else "")
        report.line(f"    language={row['language']!r} title={preview!r}: {row['cnt']} rows")

    cur.execute(
        """
        SELECT COUNT(*) AS groups FROM (
            SELECT title, language FROM ingestion.document
            WHERE title IS NOT NULL AND btrim(title) <> ''
            GROUP BY title, language HAVING COUNT(*) > 1
        ) t
        """
    )
    report.line(f"  Total duplicate (title, language) groups: {cur.fetchone()['groups']}")

    report.subheader("chunk — duplicate (document_id, text)")
    cur.execute(
        """
        SELECT document_id, LEFT(text, 80) AS text_preview, COUNT(*) AS cnt
        FROM ingestion.chunk
        WHERE text IS NOT NULL
        GROUP BY document_id, text
        HAVING COUNT(*) > 1
        ORDER BY cnt DESC
        LIMIT 20
        """
    )
    for row in cur.fetchall():
        report.line(
            f"    document_id={row['document_id']} cnt={row['cnt']} text={row['text_preview']!r}..."
        )
    cur.execute(
        """
        SELECT COUNT(*) AS groups FROM (
            SELECT document_id, text FROM ingestion.chunk
            WHERE text IS NOT NULL
            GROUP BY document_id, text HAVING COUNT(*) > 1
        ) t
        """
    )
    report.line(f"  Total duplicate (document_id, text) groups: {cur.fetchone()['groups']}")

    for table in ("ingestion.mv_portal_link", "ingestion.mv_specific_link"):
        report.subheader(f"{table} — duplicate canonical_url per language")
        cur.execute(
            f"""
            SELECT language, canonical_url, COUNT(*) AS cnt
            FROM {table}
            WHERE canonical_url IS NOT NULL AND btrim(canonical_url) <> ''
            GROUP BY language, canonical_url
            HAVING COUNT(*) > 1
            ORDER BY cnt DESC
            LIMIT 20
            """
        )
        rows = cur.fetchall()
        for row in rows:
            report.line(
                f"    language={row['language']!r} url={row['canonical_url']!r}: {row['cnt']} rows"
            )
        cur.execute(
            f"""
            SELECT COUNT(*) AS groups FROM (
                SELECT language, canonical_url FROM {table}
                WHERE canonical_url IS NOT NULL AND btrim(canonical_url) <> ''
                GROUP BY language, canonical_url HAVING COUNT(*) > 1
            ) t
            """
        )
        report.line(f"  Total duplicate (language, canonical_url) groups: {cur.fetchone()['groups']}")


def audit_redundancy(cur, report):
    report.header("4. REDUNDANCY (50 random documents)")
    cur.execute(
        """
        SELECT id, canonical_url, title, summary_ka, content_text, lead_text, meta_description
        FROM ingestion.document
        ORDER BY random()
        LIMIT 50
        """
    )
    rows = cur.fetchall()
    report.line(f"  Sample size: {len(rows)}")

    summary_prefix_match = 0
    lead_equals_meta = 0
    title_in_content = 0

    for row in rows:
        summary_ka = row.get("summary_ka") or ""
        content_text = row.get("content_text") or ""
        if summary_ka and content_text and content_text[: len(summary_ka)] == summary_ka:
            summary_prefix_match += 1

        lead = (row.get("lead_text") or "").strip()
        meta = (row.get("meta_description") or "").strip()
        if lead and meta and lead == meta:
            lead_equals_meta += 1

        title = (row.get("title") or "").strip()
        if title and title in (content_text[:100] if content_text else ""):
            title_in_content += 1

    report.line(
        f"  summary_ka equals first len(summary_ka) chars of content_text: "
        f"{summary_prefix_match} / {len(rows)}"
    )
    report.line(f"  lead_text exactly equals meta_description: {lead_equals_meta} / {len(rows)}")
    report.line(
        f"  title appears verbatim in first 100 chars of content_text: "
        f"{title_in_content} / {len(rows)}"
    )


def audit_content_quality(cur, report):
    report.header("5. CONTENT QUALITY")

    report.subheader("document")
    cur.execute(
        """
        SELECT COALESCE(page_kind::text, '<NULL>') AS page_kind, COUNT(*) AS cnt
        FROM ingestion.document
        GROUP BY page_kind
        ORDER BY cnt DESC
        """
    )
    report.line("  Breakdown by page_kind:")
    for row in cur.fetchall():
        report.line(f"    {row['page_kind']}: {row['cnt']}")

    cur.execute("SELECT COUNT(*) AS c FROM ingestion.document WHERE authority_score = 0.0")
    report.line(f"  authority_score = 0.0: {cur.fetchone()['c']}")

    cur.execute(
        """
        SELECT COUNT(*) AS c FROM ingestion.document
        WHERE keywords IS NULL OR cardinality(keywords) = 0
        """
    )
    report.line(f"  empty keywords[]: {cur.fetchone()['c']}")

    cur.execute(
        """
        SELECT COALESCE(fetch_status::text, '<NULL>') AS fetch_status, COUNT(*) AS cnt
        FROM ingestion.document
        GROUP BY fetch_status
        ORDER BY cnt DESC
        """
    )
    report.line("  Count by fetch_status:")
    for row in cur.fetchall():
        report.line(f"    {row['fetch_status']}: {row['cnt']}")

    cur.execute(
        """
        SELECT COUNT(*) AS c FROM ingestion.document
        WHERE content_text IS NULL OR length(btrim(content_text)) < 100
        """
    )
    report.line(f"  content_text length < 100 (or NULL): {cur.fetchone()['c']}")

    report.subheader("chunk")
    cur.execute(
        """
        SELECT COUNT(*) AS c FROM ingestion.chunk
        WHERE text IS NULL OR length(btrim(text)) < 50
        """
    )
    report.line(f"  text length < 50 (or NULL): {cur.fetchone()['c']}")

    cur.execute(
        """
        SELECT COUNT(*) AS c FROM ingestion.chunk
        WHERE text IS NOT NULL AND length(text) > 3000
        """
    )
    report.line(f"  text length > 3000: {cur.fetchone()['c']}")

    cur.execute(
        """
        SELECT AVG(length(text)) AS avg_len, MIN(length(text)) AS min_len, MAX(length(text)) AS max_len
        FROM ingestion.chunk
        WHERE text IS NOT NULL
        """
    )
    stats = cur.fetchone()
    avg_len = stats["avg_len"]
    avg_str = f"{float(avg_len):.2f}" if avg_len is not None else "n/a"
    report.line(
        f"  text length stats: avg={avg_str} min={stats['min_len']} max={stats['max_len']}"
    )


def audit_language_consistency(cur, report):
    report.header("6. LANGUAGE CONSISTENCY")

    cur.execute(
        """
        SELECT id, title, language
        FROM ingestion.document
        WHERE language = 'ka' AND title IS NOT NULL AND btrim(title) <> ''
        """
    )
    rows = cur.fetchall()
    mislabeled = 0
    examples = []

    for row in rows:
        title = row["title"]
        latin = len(LATIN_RE.findall(title))
        georgian = len(GEORGIAN_RE.findall(title))
        if latin > georgian:
            mislabeled += 1
            if len(examples) < 10:
                examples.append((row["id"], title))

    report.line(
        "  language='ka' documents where Latin chars > Georgian chars in title (heuristic):"
    )
    report.line(f"  Count: {mislabeled} / {len(rows)} ka documents with non-empty title")
    if examples:
        report.line("  Examples (up to 10):")
        for doc_id, title in examples:
            report.line(f"    id={doc_id} title={title[:120]!r}")


def _configure_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")


def main():
    _configure_stdio()
    report = Report()
    report.header("DATABASE DATA QUALITY AUDIT")
    report.line(f"Generated: {datetime.now(timezone.utc).isoformat()}")
    report.line(f"Database: {DB_CONFIG['dbname']} @ {DB_CONFIG['host']}:{DB_CONFIG['port']}")
    report.line("")

    try:
        conn = psycopg2.connect(**DB_CONFIG)
    except psycopg2.Error as exc:
        report.line(f"CONNECTION FAILED: {exc}")
        report.save(REPORT_PATH)
        return 1

    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            audit_null_empty(cur, report)
            report.line("")
            audit_truncation(cur, report)
            report.line("")
            audit_duplicates(cur, report)
            report.line("")
            audit_redundancy(cur, report)
            report.line("")
            audit_content_quality(cur, report)
            report.line("")
            audit_language_consistency(cur, report)
    except psycopg2.Error as exc:
        report.line("")
        report.line(f"QUERY ERROR: {exc}")
        report.save(REPORT_PATH)
        return 1
    finally:
        conn.close()

    report.line("")
    report.header("END OF REPORT")
    report.save(REPORT_PATH)
    print(f"(Report also saved to {REPORT_PATH})", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
