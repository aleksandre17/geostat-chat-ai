#!/usr/bin/env python3
"""RAG-U12 — golden-set retrieval eval harness (hit@k, MRR, NDCG@10, latency)."""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from collections import defaultdict
from typing import Any

DEFAULT_CORPUS = "geostat-portal"
DEFAULT_RETRIEVAL = "http://127.0.0.1:8092"
DEFAULT_INGESTION = "http://127.0.0.1:8093"
DEFAULT_CHAT = "http://127.0.0.1:8090"
SEARCH_K = 10

FALLBACK_QUERIES: list[dict[str, Any]] = [
    {"locale": "ka", "queryText": "statistika", "expectUrl": "geostat.ge", "minChunks": 1},
    {"locale": "ka", "queryText": "ინფლაცია", "expectUrl": "geostat.ge", "minChunks": 1},
    {"locale": "en", "queryText": "inflation", "expectUrl": "geostat.ge", "minChunks": 1},
    {"locale": "en", "queryText": "statistics", "expectUrl": "geostat.ge", "minChunks": 1},
]


def url_hit(source_url: str | None, expect_url: str | None) -> bool:
    if not expect_url:
        return True
    return expect_url.lower() in (source_url or "").lower()


def hit_at_k(hits: list[dict[str, Any]], expect_url: str | None, k: int) -> float:
    for hit in hits[:k]:
        if url_hit(hit.get("sourceUrl"), expect_url):
            return 1.0
    return 0.0


def reciprocal_rank(hits: list[dict[str, Any]], expect_url: str | None) -> float:
    for index, hit in enumerate(hits):
        if url_hit(hit.get("sourceUrl"), expect_url):
            return 1.0 / (index + 1)
    return 0.0


def ndcg_at_k(hits: list[dict[str, Any]], expect_url: str | None, k: int) -> float:
    if not expect_url:
        return 0.0
    dcg = 0.0
    for index, hit in enumerate(hits[:k]):
        rel = 1.0 if url_hit(hit.get("sourceUrl"), expect_url) else 0.0
        if rel > 0:
            dcg += rel / math.log2(index + 2)
    idcg = 1.0 / math.log2(2)
    return dcg / idcg if idcg > 0 else 0.0


def http_json(method: str, url: str, body: dict[str, Any] | None = None, timeout: float = 30.0) -> Any:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def load_queries(ingestion_base: str, corpus: str) -> list[dict[str, Any]]:
    url = f"{ingestion_base.rstrip('/')}/api/v1/ingestion/corpora/{corpus}/evaluation-queries"
    try:
        rows = http_json("GET", url)
        if isinstance(rows, list) and rows:
            print(f"Loaded {len(rows)} evaluation queries from ingestion API", file=sys.stderr)
            return rows
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"Warning: using fallback eval queries ({exc})", file=sys.stderr)
    return list(FALLBACK_QUERIES)


def search(
    retrieval_base: str,
    query_text: str,
    locale: str,
    corpus: str,
    max_chunks: int,
) -> tuple[list[dict[str, Any]], float]:
    url = f"{retrieval_base.rstrip('/')}/api/v1/retrieval/search"
    payload = {
        "text": query_text,
        "locale": locale,
        "maxChunks": max_chunks,
        "corpusName": corpus,
    }
    started = time.perf_counter()
    hits = http_json("POST", url, payload)
    elapsed_ms = (time.perf_counter() - started) * 1000.0
    if not isinstance(hits, list):
        raise RuntimeError(f"Unexpected retrieval response type: {type(hits)!r}")
    return hits, elapsed_ms


def score_query(
    hits: list[dict[str, Any]],
    expect_url: str | None,
    min_chunks: int,
) -> dict[str, float | int]:
    result_count = len(hits)
    url_ok = any(url_hit(hit.get("sourceUrl"), expect_url) for hit in hits[:SEARCH_K])
    chunk_ok = result_count >= min_chunks
    success = url_ok and chunk_ok
    return {
        "hit@1": hit_at_k(hits, expect_url, 1) if chunk_ok else 0.0,
        "hit@5": 1.0 if success else 0.0,
        "MRR": reciprocal_rank(hits, expect_url) if chunk_ok else 0.0,
        "NDCG@10": ndcg_at_k(hits, expect_url, SEARCH_K) if chunk_ok else 0.0,
        "result_count": result_count,
        "chunk_ok": int(chunk_ok),
        "url_ok": int(url_ok),
    }


def entity_f1(expected: list[dict[str, Any]] | None, actual: list[dict[str, Any]] | None) -> float | None:
    if not expected:
        return None
    expected_norm = {
        str(item.get("normalizedForm") or item.get("value") or "").strip().lower()
        for item in expected
        if (item.get("normalizedForm") or item.get("value"))
    }
    expected_norm = {value for value in expected_norm if value}
    if not expected_norm:
        return None
    actual_norm = {
        str(item.get("normalizedForm") or item.get("value") or "").strip().lower()
        for item in (actual or [])
        if (item.get("normalizedForm") or item.get("value"))
    }
    actual_norm = {value for value in actual_norm if value}
    if not actual_norm:
        return 0.0
    overlap = len(expected_norm & actual_norm)
    precision = overlap / len(actual_norm)
    recall = overlap / len(expected_norm)
    if precision + recall == 0:
        return 0.0
    return 2 * precision * recall / (precision + recall)


def analyze_query(chat_base: str | None, text: str, locale: str) -> dict[str, Any] | None:
    if not chat_base:
        return None
    url = f"{chat_base.rstrip('/')}/api/v1/chat/query:analyze"
    try:
        result = http_json("POST", url, {"text": text, "locale": locale})
        return result if isinstance(result, dict) else None
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"Warning: query analyze failed ({exc})", file=sys.stderr)
        return None


def intent_match(expected: str | None, actual: str | None) -> float | None:
    if not expected or not actual:
        return None
    return 1.0 if expected.strip().lower() == actual.strip().lower() else 0.0


def aggregate_metrics(per_query: list[dict[str, Any]]) -> dict[str, float | int | None]:
    count = len(per_query)
    if count == 0:
        return {
            "queries": 0,
            "hit@1": 0.0,
            "hit@5": 0.0,
            "MRR": 0.0,
            "NDCG@10": 0.0,
            "mean_response_ms": 0.0,
            "intent_accuracy": None,
            "entity_F1": None,
        }
    intent_scores = [q["intent_match"] for q in per_query if q.get("intent_match") is not None]
    entity_scores = [q["entity_f1"] for q in per_query if q.get("entity_f1") is not None]
    return {
        "queries": count,
        "hit@1": sum(q["hit@1"] for q in per_query) / count,
        "hit@5": sum(q["hit@5"] for q in per_query) / count,
        "MRR": sum(q["MRR"] for q in per_query) / count,
        "NDCG@10": sum(q["NDCG@10"] for q in per_query) / count,
        "mean_response_ms": sum(q["response_ms"] for q in per_query) / count,
        "intent_accuracy": sum(intent_scores) / len(intent_scores) if intent_scores else None,
        "entity_F1": sum(entity_scores) / len(entity_scores) if entity_scores else None,
    }


LOWER_IS_BETTER = frozenset({"mean_response_ms"})


def compare_baseline(
    metrics: dict[str, float | int | None],
    baseline: dict[str, Any],
    max_regression: float,
) -> list[str]:
    failures: list[str] = []
    base_metrics = baseline.get("metrics", {})
    for key, base_value in base_metrics.items():
        if base_value is None:
            continue
        current = metrics.get(key)
        if current is None:
            continue
        if not isinstance(base_value, (int, float)):
            continue
        if key in LOWER_IS_BETTER:
            if current > base_value * (1.0 + max_regression):
                failures.append(
                    f"{key}: {current:.4f} > baseline {base_value:.4f} "
                    f"(max regression {max_regression:.0%})"
                )
        elif current < base_value * (1.0 - max_regression):
            failures.append(
                f"{key}: {current:.4f} < baseline {base_value:.4f} "
                f"(max regression {max_regression:.0%})"
            )
    return failures


def aggregate_buckets(
    per_query: list[dict[str, Any]], field: str
) -> dict[str, dict[str, float | int | None]]:
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in per_query:
        key = str(row.get(field) or "unknown")
        groups[key].append(row)
    return {name: aggregate_metrics(rows) for name, rows in sorted(groups.items())}


def write_report(report_path: Path, payload: dict[str, Any]) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="RAG-U12 golden-set eval harness")
    parser.add_argument("--corpus", default=os.environ.get("EVAL_CORPUS", DEFAULT_CORPUS))
    parser.add_argument("--retrieval-base", default=os.environ.get("RETRIEVAL_BASE_URL", DEFAULT_RETRIEVAL))
    parser.add_argument("--ingestion-base", default=os.environ.get("INGESTION_URL", DEFAULT_INGESTION))
    parser.add_argument(
        "--chat-base",
        default=os.environ.get("CHAT_BASE_URL"),
        help="chat-api base URL for query:analyze (intent/entity metrics)",
    )
    parser.add_argument("--baseline", type=Path, default=Path("ops/eval/baseline.json"))
    parser.add_argument(
        "--reference-baseline",
        type=Path,
        default=None,
        help="Frozen pre-cutover baseline (e.g. baseline.yaml-frozen.json); current run must not regress vs reference",
    )
    parser.add_argument(
        "--reference-max-regression",
        type=float,
        default=0.0,
        help="Max allowed drop vs --reference-baseline (0 = strict cutover gate)",
    )
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--max-regression", type=float, default=0.05)
    parser.add_argument("--write-baseline", action="store_true")
    parser.add_argument(
        "--write-yaml-frozen",
        action="store_true",
        help="Capture YAML-era metrics into ops/eval/baseline.yaml-frozen.json (owner step 0, pre-cutover)",
    )
    parser.add_argument("--dry-run", action="store_true", help="Load queries only; skip retrieval calls")
    parser.add_argument(
        "--min-queries",
        type=int,
        default=0,
        help="Fail if loaded query count is below this (0 = no check)",
    )
    args = parser.parse_args(argv)

    queries = load_queries(args.ingestion_base, args.corpus)
    if args.min_queries > 0 and len(queries) < args.min_queries:
        print(
            f"ERROR: {len(queries)} queries loaded; need at least {args.min_queries}",
            file=sys.stderr,
        )
        return 1
    per_query: list[dict[str, Any]] = []

    if args.dry_run:
        metrics = aggregate_metrics([])
        metrics["queries"] = len(queries)
        report = {
            "version": 1,
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "corpus": args.corpus,
            "metrics": metrics,
            "perQuery": [],
            "dryRun": True,
        }
        report_path = args.report or Path(
            f"ops/eval/reports/{datetime.now(timezone.utc).strftime('%Y-%m-%d')}-dry-run.json"
        )
        write_report(report_path, report)
        print(json.dumps(metrics, ensure_ascii=False, indent=2))
        return 0

    for query in queries:
        text = query["queryText"]
        locale = query["locale"]
        expect_url = query.get("expectUrl")
        min_chunks = int(query.get("minChunks") or 1)
        expected_intent = query.get("expectedIntent")
        expected_entities = query.get("expectedEntities")
        difficulty = query.get("difficulty")
        analyze = analyze_query(args.chat_base, text, locale)
        actual_intent = analyze.get("intent") if analyze else None
        actual_entities = analyze.get("entities") if analyze else None
        try:
            hits, elapsed_ms = search(
                args.retrieval_base, text, locale, args.corpus, SEARCH_K
            )
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            print(f"[FAIL] {locale}: {text} -> retrieval error: {exc}", file=sys.stderr)
            hits = []
            elapsed_ms = 0.0

        scored = score_query(hits, expect_url, min_chunks)
        row = {
            "locale": locale,
            "queryText": text,
            "expectUrl": expect_url,
            "expectedIntent": expected_intent,
            "actualIntent": actual_intent,
            "intent_match": intent_match(expected_intent, actual_intent),
            "entity_f1": entity_f1(expected_entities, actual_entities),
            "difficulty": difficulty,
            "hit@1": scored["hit@1"],
            "hit@5": scored["hit@5"],
            "MRR": scored["MRR"],
            "NDCG@10": scored["NDCG@10"],
            "response_ms": elapsed_ms,
            "result_count": scored["result_count"],
            "minChunks": min_chunks,
        }
        per_query.append(row)
        status = "OK" if row["hit@5"] >= 1.0 else "MISS"
        print(
            f"[{status}] {locale}: {text} hit@5={row['hit@5']:.0f} "
            f"MRR={row['MRR']:.3f} {elapsed_ms:.0f}ms",
            file=sys.stderr,
        )

    metrics = aggregate_metrics(per_query)
    buckets = {
        "locale": aggregate_buckets(per_query, "locale"),
        "intent": aggregate_buckets(per_query, "expectedIntent"),
        "difficulty": aggregate_buckets(per_query, "difficulty"),
    }

    report = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "corpus": args.corpus,
        "metrics": metrics,
        "buckets": buckets,
        "perQuery": per_query,
    }

    report_path = args.report or Path(
        f"ops/eval/reports/{datetime.now(timezone.utc).strftime('%Y-%m-%d')}.json"
    )
    write_report(report_path, report)
    print(json.dumps(metrics, ensure_ascii=False, indent=2))

    if args.write_baseline:
        baseline_payload = {
            "version": 1,
            "corpus": args.corpus,
            "metrics": {k: v for k, v in metrics.items() if k != "queries" and v is not None},
        }
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(
            json.dumps(baseline_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"Wrote baseline {args.baseline}", file=sys.stderr)
        return 0

    if args.write_yaml_frozen:
        frozen_path = Path("ops/eval/baseline.yaml-frozen.json")
        frozen_payload = {
            "version": 1,
            "corpus": args.corpus,
            "catalogSource": "yaml",
            "frozen": True,
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "note": "Pre-cutover YAML-era retrieval baseline — capture via rag-eval-freeze-yaml-baseline.ps1 only.",
            "metrics": {k: v for k, v in metrics.items() if k != "queries" and v is not None},
        }
        frozen_path.parent.mkdir(parents=True, exist_ok=True)
        frozen_path.write_text(
            json.dumps(frozen_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"Wrote YAML frozen baseline {frozen_path}", file=sys.stderr)
        return 0

    if args.baseline.exists():
        baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
        failures = compare_baseline(metrics, baseline, args.max_regression)
        if failures:
            for failure in failures:
                print(f"REGRESSION: {failure}", file=sys.stderr)
            return 1
    else:
        print(f"Warning: baseline missing at {args.baseline}; run with --write-baseline", file=sys.stderr)

    if args.reference_baseline is not None and args.reference_baseline.exists():
        reference = json.loads(args.reference_baseline.read_text(encoding="utf-8"))
        ref_failures = compare_baseline(metrics, reference, args.reference_max_regression)
        if ref_failures:
            for failure in ref_failures:
                print(f"YAML-REFERENCE: {failure}", file=sys.stderr)
            return 1
        print(
            f"Reference baseline OK ({args.reference_baseline.name})",
            file=sys.stderr,
        )
    elif args.reference_baseline is not None:
        print(
            f"Warning: reference baseline missing at {args.reference_baseline}",
            file=sys.stderr,
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
