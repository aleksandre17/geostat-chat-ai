#!/usr/bin/env python3
"""Random sample audit across ingestion.* tables (Layer 1 + enrichment quality)."""
from __future__ import annotations

import json
import re
import sys
from collections import Counter, defaultdict

import psycopg2
import psycopg2.extras

DSN = {
    "host": "127.0.0.1",
    "port": 5432,
    "dbname": "geostat",
    "user": "geostat",
    "password": "geostat-dev-change-me",
    "options": "-c search_path=ingestion,public",
}

SAMPLE_N = 100

BOILERPLATE_MARKERS = [
    "ვებგვერდის ადაპტ",
    "adapted version of the website",
    "skip to content",
    "უკან დაბრუნება",
    "official statistics of georgia",
    "საქსტატის ოფიციალური ვებგვერდი",
    "ცენტრალური ოფისი:",
    "central office:",
]

NAV_PATH_HINTS = (
    "/about",
    "/contact",
    "/privacy",
    "/sitemap",
    "/search",
)


def pct(n: int, d: int) -> str:
    if d == 0:
        return "n/a"
    return f"{100.0 * n / d:.1f}%"


def has_boilerplate(text: str | None) -> bool:
    if not text:
        return False
    t = re.sub(r"\s+", " ", text.strip().lower())
    return any(m.lower() in t for m in BOILERPLATE_MARKERS)


def looks_like_nav_url(url: str | None) -> bool:
    if not url:
        return False
    p = url.lower()
    return any(h in p for h in NAV_PATH_HINTS)


def short_text(text: str | None, min_chars: int = 100) -> bool:
    return text is None or len(text.strip()) < min_chars


def main() -> int:
    conn = psycopg2.connect(**DSN)
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

    cur.execute(
        """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'ingestion' AND table_type = 'BASE TABLE'
        ORDER BY table_name
        """
    )
    tables = [r["table_name"] for r in cur.fetchall()]

    print("=== ingestion schema random sample audit ===")
    print(f"sample_size={SAMPLE_N} per table\n")

    table_counts: dict[str, int] = {}
    for t in tables:
        cur.execute(f"SELECT COUNT(*) AS c FROM ingestion.{t}")
        table_counts[t] = cur.fetchone()["c"]

    for t in tables:
        total = table_counts[t]
        n = min(SAMPLE_N, total)
        print(f"## {t}  (total={total}, sampled={n})")
        if n == 0:
            print("  (empty)\n")
            continue

        cur.execute(
            f"SELECT * FROM ingestion.{t} ORDER BY random() LIMIT %s",
            (n,),
        )
        rows = cur.fetchall()
        cols = list(rows[0].keys()) if rows else []
        print(f"  columns: {', '.join(cols[:12])}{'…' if len(cols) > 12 else ''}")

        if t == "document":
            analyze_documents(rows)
        elif t == "chunk":
            analyze_chunks(rows)
        elif t == "url_frontier":
            analyze_frontier(rows)
        elif t == "enrichment_run":
            analyze_enrichment_runs(rows)
        elif t == "evaluation_query":
            analyze_eval_queries(rows)
        else:
            print(f"  sample keys only (no deep audit hook)")
        print()

    print_gate_summary(cur)
    conn.close()
    return 0


def print_gate_summary(cur) -> None:
    """Aggregate gate metrics aligned to ops/eval/corpus-quality-gate.yaml ids."""
    cur.execute(
        """
        SELECT c.id AS corpus_id, c.name AS corpus_name
        FROM ingestion.corpus c
        WHERE c.name = 'geostat-portal'
        LIMIT 1
        """
    )
    row = cur.fetchone()
    if not row:
        print("=== P0 gate summary (no geostat-portal corpus) ===")
        return

    corpus_id = row["corpus_id"]
    cur.execute(
        """
        SELECT COUNT(*) AS parsed FROM ingestion.document
        WHERE corpus_id = %s AND fetch_status = 'parsed'
        """,
        (corpus_id,),
    )
    parsed = cur.fetchone()["parsed"] or 0
    if parsed == 0:
        print("=== P0 gate summary (no parsed docs) ===")
        return

    cur.execute(
        """
        SELECT COUNT(*) AS n FROM ingestion.document
        WHERE corpus_id = %s AND fetch_status = 'parsed'
          AND (content_text ILIKE '%%adapted version of the website%%'
               OR content_text ILIKE '%%ვებგვერდის ადაპტ%%')
        """,
        (corpus_id,),
    )
    boiler = cur.fetchone()["n"]
    cur.execute(
        """
        SELECT COUNT(*) AS n FROM ingestion.document
        WHERE corpus_id = %s AND fetch_status = 'parsed'
          AND COALESCE(length(content_text), 0) < 30
        """,
        (corpus_id,),
    )
    empty = cur.fetchone()["n"]
    cur.execute(
        """
        SELECT COUNT(DISTINCT document_id) AS n FROM ingestion.chunk
        WHERE corpus_id = %s
        """,
        (corpus_id,),
    )
    chunked = cur.fetchone()["n"]
    cur.execute(
        """
        SELECT COUNT(*) AS n FROM ingestion.document
        WHERE corpus_id = %s AND fetch_status = 'parsed'
          AND (COALESCE(summary_ka, '') <> '' OR COALESCE(summary_en, '') <> '')
        """,
        (corpus_id,),
    )
    summary = cur.fetchone()["n"]

    gates = [
        ("boilerplate_ratio", boiler / parsed, "<= 0.05"),
        ("empty_body_rate", empty / parsed, "<= 0.03"),
        ("chunk_coverage", chunked / parsed, ">= 0.95"),
        ("summary_coverage", summary / parsed, ">= 0.95"),
    ]
    print("=== P0 gate summary (corpus-quality-gate.yaml ids) ===")
    failed = []
    for gate_id, value, target in gates:
        if gate_id in ("boilerplate_ratio", "empty_body_rate"):
            passed = value <= float(target.split()[-1])
        else:
            passed = value >= float(target.split()[-1])
        status = "PASS" if passed else "FAIL"
        print(f"  {gate_id}: {value:.4f} target {target} [{status}]")
        if not passed:
            failed.append(gate_id)
    if failed:
        print(f"  BLOCKED gates: {', '.join(failed)}")


def analyze_documents(rows: list) -> None:
    n = len(rows)
    short = sum(1 for r in rows if short_text(r.get("content_text"), 100))
    very_short = sum(1 for r in rows if short_text(r.get("content_text"), 30))
    boiler = sum(1 for r in rows if has_boilerplate(r.get("content_text")))
    nav_url = sum(1 for r in rows if looks_like_nav_url(r.get("canonical_url")))
    blank_title = sum(1 for r in rows if not (r.get("title") or "").strip())
    no_summary = sum(
        1
        for r in rows
        if not (r.get("summary_ka") or "").strip() and not (r.get("summary_en") or "").strip()
    )
    page_kinds = Counter(r.get("page_kind") or "null" for r in rows)
    langs = Counter(r.get("language") or "null" for r in rows)
    lengths = [len((r.get("content_text") or "").strip()) for r in rows]
    avg_len = sum(lengths) / n if n else 0

    print(f"  content_text avg_len={avg_len:.0f} chars")
    print(f"  short (<100 chars): {short}/{n} = {pct(short, n)}")
    print(f"  very_short (<30 chars): {very_short}/{n} = {pct(very_short, n)}")
    print(f"  boilerplate marker in body: {boiler}/{n} = {pct(boiler, n)}")
    print(f"  nav-like URL: {nav_url}/{n} = {pct(nav_url, n)}")
    print(f"  blank title: {blank_title}/{n} = {pct(blank_title, n)}")
    print(f"  no summary_ka/en: {no_summary}/{n} = {pct(no_summary, n)}")
    print(f"  page_kind: {dict(page_kinds.most_common(8))}")
    print(f"  language: {dict(langs.most_common(5))}")

    bad = [r for r in rows if short_text(r.get("content_text"), 100) or has_boilerplate(r.get("content_text"))]
    if bad:
        print("  examples (weak body):")
        for r in bad[:3]:
            txt = (r.get("content_text") or "")[:120].replace("\n", " ")
            print(f"    - {r.get('canonical_url')}")
            print(f"      title={((r.get('title') or '')[:60])!r} len={len((r.get('content_text') or ''))} kind={r.get('page_kind')}")
            print(f"      text={txt!r}…")


def analyze_chunks(rows: list) -> None:
    n = len(rows)
    short = sum(1 for r in rows if short_text(r.get("content_text"), 50))
    boiler = sum(1 for r in rows if has_boilerplate(r.get("content_text")))
    lengths = [len((r.get("content_text") or "").strip()) for r in rows]
    avg_len = sum(lengths) / n if n else 0
    print(f"  chunk text avg_len={avg_len:.0f} chars")
    print(f"  short (<50 chars): {short}/{n} = {pct(short, n)}")
    print(f"  boilerplate in chunk: {boiler}/{n} = {pct(boiler, n)}")


def analyze_frontier(rows: list) -> None:
    n = len(rows)
    nav = sum(1 for r in rows if looks_like_nav_url(r.get("url")))
    statuses = Counter(r.get("status") for r in rows)
    depths = Counter(r.get("depth") for r in rows)
    print(f"  nav-like URL: {nav}/{n} = {pct(nav, n)}")
    print(f"  status: {dict(statuses)}")
    print(f"  depth: {dict(sorted(depths.items()))}")


def analyze_enrichment_runs(rows: list) -> None:
    n = len(rows)
    statuses = Counter(r.get("status") for r in rows)
    kinds = Counter(r.get("deriver_kind") for r in rows)
    failed = [r for r in rows if r.get("status") == "failed"]
    print(f"  status: {dict(statuses)}")
    print(f"  deriver_kind: {dict(kinds.most_common(10))}")
    if failed:
        err_counts = Counter((r.get("error") or "")[:80] for r in failed)
        print(f"  top failures: {dict(err_counts.most_common(5))}")


def analyze_eval_queries(rows: list) -> None:
    intents = Counter(r.get("expected_intent") or "null" for r in rows)
    print(f"  expected_intent: {dict(intents.most_common(10))}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except psycopg2.OperationalError as e:
        print(f"DB connection failed: {e}", file=sys.stderr)
        raise SystemExit(1)
