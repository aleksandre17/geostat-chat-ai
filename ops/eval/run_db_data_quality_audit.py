#!/usr/bin/env python3
"""PostgreSQL data quality audit for ingestion schema tables."""

from __future__ import annotations

import re
import sys
from datetime import datetime, timezone
from io import StringIO

import psycopg2
from psycopg2.extras import RealDictCursor

CONN = dict(
    host="127.0.0.1",
    port=5432,
    dbname="geostat",
    user="geostat",
    password="geostat-dev-change-me",
)

SENTENCE_END = frozenset(".!?\"»")
CUTOFF_LENGTHS = (500, 1000, 2000, 5000)
GEORGIAN_RE = re.compile(r"[\u10D0-\u10FF]")
LATIN_RE = re.compile(r"[a-zA-Z]")


def connect():
    return psycopg2.connect(**CONN)


def is_null_or_empty(val) -> bool:
    if val is None:
        return True
    if isinstance(val, str) and val.strip() == "":
        return True
    return False


def pct(n: int, total: int) -> str:
    if total == 0:
        return "0.00%"
    return f"{100.0 * n / total:.2f}%"


def snippet(text, max_len: int = 120) -> str:
    if not text:
        return ""
    s = text.replace("\n", " ").replace("\r", " ").strip()
    if len(s) > max_len:
        return s[: max_len - 3] + "..."
    return s


def ends_mid_word_or_cutoff(text) -> list:
    if not text or not str(text).strip():
        return []
    flags = []
    t = str(text).rstrip()
    if not t:
        return []
    last = t[-1]
    if last not in SENTENCE_END and not last.isspace():
        if last.isalnum() or last in "-–—,/;:":
            flags.append("ends_without_sentence_punctuation")
    if len(text) in CUTOFF_LENGTHS:
        flags.append(f"length_exactly_{len(text)}")
    first_content = t.lstrip()
    if first_content and first_content[0].islower():
        flags.append("starts_mid_sentence_lowercase")
    return flags


def summary_repeats_content_prefix(summary, content) -> bool:
    if is_null_or_empty(summary) or is_null_or_empty(content):
        return False
    s = summary.strip()
    c = content.strip()
    if len(s) < 20:
        return False
    if c.startswith(s):
        return True
    n = min(len(s), len(c))
    if n >= 30 and c[:n] == s[:n]:
        return True
    return False


class Report:
    def __init__(self, out) -> None:
        self.out = out
        self.lines = []

    def writeln(self, line: str = "") -> None:
        self.lines.append(line)
        print(line, file=self.out)

    def section(self, table: str, check: str) -> None:
        self.writeln("")
        self.writeln(f"Table: {table}")
        self.writeln(f"Check: {check}")
        self.writeln("-" * 72)

    def result(self, *parts: str) -> None:
        for p in parts:
            self.writeln(f"  {p}")

    def get_text(self) -> str:
        return "\n".join(self.lines) + "\n"


def null_empty_checks(cur, report: Report) -> None:
    doc_cols = [
        "title", "canonical_url", "summary_ka", "summary_en", "keywords",
        "content_text", "lead_text", "meta_description", "page_kind",
    ]
    cur.execute("SELECT COUNT(*) AS n FROM ingestion.document")
    doc_total = cur.fetchone()["n"]
    report.section("ingestion.document", "NULL / empty checks")
    report.result(f"total_rows: {doc_total}")
    for col in doc_cols:
        if col == "keywords":
            cur.execute(
                """
                SELECT COUNT(*) AS n FROM ingestion.document
                WHERE keywords IS NULL OR cardinality(keywords) = 0
                """
            )
        else:
            cur.execute(
                f"""
                SELECT COUNT(*) AS n FROM ingestion.document
                WHERE {col} IS NULL OR TRIM({col}::text) = ''
                """
            )
        n = cur.fetchone()["n"]
        report.result(f"{col}: {n} ({pct(n, doc_total)})")

    cur.execute("SELECT COUNT(*) AS n FROM ingestion.chunk")
    chunk_total = cur.fetchone()["n"]
    report.section("ingestion.chunk", "NULL / empty checks")
    report.result(f"total_rows: {chunk_total}")
    cur.execute(
        "SELECT COUNT(*) AS n FROM ingestion.chunk WHERE text IS NULL OR TRIM(text) = ''"
    )
    n = cur.fetchone()["n"]
    report.result(f"text: {n} ({pct(n, chunk_total)})")

    cur.execute("SELECT COUNT(*) AS n FROM ingestion.topic_cluster")
    tc_total = cur.fetchone()["n"]
    report.section("ingestion.topic_cluster", "NULL / empty checks")
    report.result(f"total_rows: {tc_total}")
    for col in ("label_ka", "label_en"):
        cur.execute(
            f"""
            SELECT COUNT(*) AS n FROM ingestion.topic_cluster
            WHERE {col} IS NULL OR TRIM({col}) = ''
            """
        )
        n = cur.fetchone()["n"]
        report.result(f"{col}: {n} ({pct(n, tc_total)})")

    mv_null_specs = {
        "mv_topic_keywords": ["keyword"],
        "mv_portal_link": ["title", "summary", "canonical_url"],
        "mv_specific_link": ["title", "summary", "canonical_url"],
    }
    for mv, cols in mv_null_specs.items():
        cur.execute(f"SELECT COUNT(*) AS n FROM ingestion.{mv}")
        mv_total = cur.fetchone()["n"]
        report.section(f"ingestion.{mv}", "NULL / empty checks")
        report.result(f"total_rows: {mv_total}")
        if mv == "mv_topic_keywords":
            report.result("note: mv_topic_keywords has keyword (no title/summary/canonical_url columns)")
        for col in cols:
            cur.execute(
                f"""
                SELECT COUNT(*) AS n FROM ingestion.{mv}
                WHERE {col} IS NULL OR TRIM({col}::text) = ''
                """
            )
            n = cur.fetchone()["n"]
            report.result(f"{col}: {n} ({pct(n, mv_total)})")


def truncation_check(cur, report: Report) -> None:
    doc_text_cols = [
        "title", "summary_ka", "summary_en", "content_text", "lead_text", "meta_description",
    ]

    def audit_sample(table, sample_sql, url_col, text_cols):
        report.section(table, "Truncation check (200 random rows)")
        cur.execute(sample_sql)
        rows = cur.fetchall()
        report.result(f"sample_size: {len(rows)}")
        counts = {c: 0 for c in text_cols}
        examples = []
        for row in rows:
            url = row.get(url_col) or row.get("canonical_url") or ""
            for col in text_cols:
                text = row.get(col)
                flags = ends_mid_word_or_cutoff(text if isinstance(text, str) else None)
                if flags:
                    counts[col] += 1
                    if len(examples) < 15:
                        examples.append(
                            f"  [{col}] flags={','.join(flags)} url={url!r} snippet={snippet(text)!r}"
                        )
        for col in text_cols:
            report.result(f"{col}_rows_with_truncation_signals: {counts[col]}")
        if examples:
            report.result("examples:")
            for ex in examples:
                report.result(ex)
        else:
            report.result("examples: (none in sample)")

    audit_sample(
        "ingestion.document",
        """
        SELECT id, canonical_url, title, summary_ka, summary_en,
               content_text, lead_text, meta_description
        FROM ingestion.document ORDER BY RANDOM() LIMIT 200
        """,
        "canonical_url",
        doc_text_cols,
    )
    audit_sample(
        "ingestion.chunk",
        """
        SELECT c.id, c.text, d.canonical_url
        FROM ingestion.chunk c
        LEFT JOIN ingestion.document d ON d.id = c.document_id
        ORDER BY RANDOM() LIMIT 200
        """,
        "canonical_url",
        ["text"],
    )


def duplicate_check(cur, report: Report) -> None:
    report.section("ingestion.document", "Duplicate check")
    cur.execute(
        """
        SELECT COUNT(*) AS dup_groups FROM (
            SELECT canonical_url FROM ingestion.document
            WHERE canonical_url IS NOT NULL AND TRIM(canonical_url) <> ''
            GROUP BY canonical_url HAVING COUNT(*) > 1
        ) t
        """
    )
    report.result(f"duplicate_canonical_url_groups: {cur.fetchone()['dup_groups']}")
    cur.execute(
        """
        SELECT COALESCE(SUM(cnt - 1), 0)::bigint AS extra_rows FROM (
            SELECT COUNT(*) AS cnt FROM ingestion.document
            WHERE canonical_url IS NOT NULL AND TRIM(canonical_url) <> ''
            GROUP BY canonical_url HAVING COUNT(*) > 1
        ) t
        """
    )
    report.result(f"duplicate_canonical_url_extra_rows: {cur.fetchone()['extra_rows']}")
    cur.execute(
        """
        SELECT COUNT(*) AS dup_groups FROM (
            SELECT title, language FROM ingestion.document
            WHERE title IS NOT NULL AND TRIM(title) <> ''
            GROUP BY title, language HAVING COUNT(*) > 1
        ) t
        """
    )
    report.result(f"duplicate_title_language_groups: {cur.fetchone()['dup_groups']}")
    cur.execute(
        """
        SELECT COALESCE(SUM(cnt - 1), 0)::bigint AS extra_rows FROM (
            SELECT COUNT(*) AS cnt FROM ingestion.document
            WHERE title IS NOT NULL AND TRIM(title) <> ''
            GROUP BY title, language HAVING COUNT(*) > 1
        ) t
        """
    )
    report.result(f"duplicate_title_language_extra_rows: {cur.fetchone()['extra_rows']}")

    report.section("ingestion.chunk", "Duplicate check")
    cur.execute(
        """
        SELECT COUNT(*) AS dup_groups FROM (
            SELECT document_id, text FROM ingestion.chunk
            GROUP BY document_id, text HAVING COUNT(*) > 1
        ) t
        """
    )
    report.result(f"duplicate_document_id_text_groups: {cur.fetchone()['dup_groups']}")
    cur.execute(
        """
        SELECT COALESCE(SUM(cnt - 1), 0)::bigint AS extra_rows FROM (
            SELECT COUNT(*) AS cnt FROM ingestion.chunk
            GROUP BY document_id, text HAVING COUNT(*) > 1
        ) t
        """
    )
    report.result(f"duplicate_document_id_text_extra_rows: {cur.fetchone()['extra_rows']}")

    for mv in ("mv_portal_link", "mv_specific_link"):
        report.section(f"ingestion.{mv}", "Duplicate check (canonical_url within language)")
        cur.execute(
            f"""
            SELECT COUNT(*) AS dup_groups FROM (
                SELECT canonical_url, language FROM ingestion.{mv}
                WHERE canonical_url IS NOT NULL AND TRIM(canonical_url) <> ''
                GROUP BY canonical_url, language HAVING COUNT(*) > 1
            ) t
            """
        )
        report.result(f"duplicate_canonical_url_language_groups: {cur.fetchone()['dup_groups']}")
        cur.execute(
            f"""
            SELECT COALESCE(SUM(cnt - 1), 0)::bigint AS extra_rows FROM (
                SELECT COUNT(*) AS cnt FROM ingestion.{mv}
                WHERE canonical_url IS NOT NULL AND TRIM(canonical_url) <> ''
                GROUP BY canonical_url, language HAVING COUNT(*) > 1
            ) t
            """
        )
        report.result(f"duplicate_canonical_url_language_extra_rows: {cur.fetchone()['extra_rows']}")


def redundancy_check(cur, report: Report) -> None:
    report.section("ingestion.document", "Redundancy check (50 random rows)")
    cur.execute(
        """
        SELECT id, canonical_url, title, summary_ka, content_text, lead_text, meta_description
        FROM ingestion.document ORDER BY RANDOM() LIMIT 50
        """
    )
    rows = cur.fetchall()
    summary_prefix = lead_equals_meta = title_in_content_100 = 0
    for row in rows:
        if summary_repeats_content_prefix(row.get("summary_ka"), row.get("content_text")):
            summary_prefix += 1
        lead = (row.get("lead_text") or "").strip()
        meta = (row.get("meta_description") or "").strip()
        if lead and meta and lead == meta:
            lead_equals_meta += 1
        title = (row.get("title") or "").strip()
        content = (row.get("content_text") or "")[:100]
        if title and title in content:
            title_in_content_100 += 1
    n = len(rows)
    report.result(f"sample_size: {n}")
    report.result(f"summary_ka_repeats_content_text_prefix: {summary_prefix} ({pct(summary_prefix, n)})")
    report.result(f"lead_text_equals_meta_description: {lead_equals_meta} ({pct(lead_equals_meta, n)})")
    report.result(f"title_in_content_text_first_100_chars: {title_in_content_100} ({pct(title_in_content_100, n)})")


def content_quality(cur, report: Report) -> None:
    report.section("ingestion.document", "Content quality")
    cur.execute(
        """
        SELECT COALESCE(page_kind::text, '(null)') AS page_kind, COUNT(*) AS n
        FROM ingestion.document GROUP BY page_kind ORDER BY n DESC
        """
    )
    report.result("count_by_page_kind:")
    for row in cur.fetchall():
        report.result(f"  {row['page_kind']}: {row['n']}")
    cur.execute("SELECT COUNT(*) AS n FROM ingestion.document WHERE authority_score = 0")
    report.result(f"authority_score_zero: {cur.fetchone()['n']}")
    cur.execute(
        "SELECT COUNT(*) AS n FROM ingestion.document WHERE keywords IS NULL OR cardinality(keywords) = 0"
    )
    report.result(f"empty_keywords_array: {cur.fetchone()['n']}")
    cur.execute(
        """
        SELECT COALESCE(fetch_status::text, '(null)') AS fetch_status, COUNT(*) AS n
        FROM ingestion.document GROUP BY fetch_status ORDER BY n DESC
        """
    )
    report.result("count_by_fetch_status:")
    for row in cur.fetchall():
        report.result(f"  {row['fetch_status']}: {row['n']}")

    report.section("ingestion.chunk", "Content quality")
    cur.execute("SELECT COUNT(*) AS n FROM ingestion.chunk")
    chunk_total = cur.fetchone()["n"]
    cur.execute("SELECT COUNT(*) AS n FROM ingestion.chunk WHERE LENGTH(text) < 50")
    short_n = cur.fetchone()["n"]
    report.result(f"very_short_chunks_lt_50_chars: {short_n} ({pct(short_n, chunk_total)})")
    cur.execute("SELECT COUNT(*) AS n FROM ingestion.chunk WHERE LENGTH(text) > 3000")
    long_n = cur.fetchone()["n"]
    report.result(f"very_long_chunks_gt_3000_chars: {long_n} ({pct(long_n, chunk_total)})")


def language_consistency(cur, report: Report) -> None:
    report.section("ingestion.document", "Language consistency (language=ka, Latin-dominant title)")
    cur.execute(
        "SELECT id, canonical_url, title FROM ingestion.document WHERE language = 'ka'"
    )
    rows = cur.fetchall()
    total_ka = len(rows)
    latin_dominant = 0
    examples = []
    for row in rows:
        title = row.get("title") or ""
        latin = len(LATIN_RE.findall(title))
        geo = len(GEORGIAN_RE.findall(title))
        if latin > geo:
            latin_dominant += 1
            if len(examples) < 10:
                examples.append(
                    f"  url={row.get('canonical_url')!r} latin={latin} georgian={geo} title={snippet(title, 80)!r}"
                )
    report.result(f"language_ka_rows: {total_ka}")
    report.result(f"latin_dominant_title_among_ka: {latin_dominant} ({pct(latin_dominant, total_ka)})")
    if examples:
        report.result("examples:")
        for ex in examples:
            report.result(ex)
    else:
        report.result("examples: (none)")


def run_audit(report_path=None) -> int:
    buffer = StringIO()
    report = Report(buffer)
    report.writeln("=" * 72)
    report.writeln("PostgreSQL Data Quality Audit — schema: ingestion")
    report.writeln(f"Generated: {datetime.now(timezone.utc).isoformat()}")
    report.writeln("=" * 72)
    try:
        conn = connect()
    except psycopg2.Error as e:
        print(f"Connection failed: {e}", file=sys.stderr)
        return 1
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            null_empty_checks(cur, report)
            truncation_check(cur, report)
            duplicate_check(cur, report)
            redundancy_check(cur, report)
            content_quality(cur, report)
            language_consistency(cur, report)
    except psycopg2.Error as e:
        print(f"Audit failed: {e}", file=sys.stderr)
        return 1
    finally:
        conn.close()
    text = report.get_text()
    if report_path:
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(text)
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "ops/eval/reports/2026-05-26-db-data-quality-audit.txt"
    raise SystemExit(run_audit(path))
