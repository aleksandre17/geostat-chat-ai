#!/usr/bin/env python3
"""Unit tests for RAG-U12 eval metric helpers."""
import importlib.util
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("run_eval", ROOT / "run-eval.py")
run_eval = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(run_eval)


class EvalMetricsTest(unittest.TestCase):
    def test_hit_at_k_first_result(self):
        hits = [{"sourceUrl": "https://www.geostat.ge/ka/statistics"}]
        self.assertEqual(run_eval.hit_at_k(hits, "geostat.ge", 1), 1.0)

    def test_hit_at_k_miss(self):
        hits = [{"sourceUrl": "https://example.com/other"}]
        self.assertEqual(run_eval.hit_at_k(hits, "geostat.ge", 5), 0.0)

    def test_mrr_second_rank(self):
        hits = [
            {"sourceUrl": "https://example.com/other"},
            {"sourceUrl": "https://www.geostat.ge/en/inflation"},
        ]
        self.assertAlmostEqual(run_eval.reciprocal_rank(hits, "geostat.ge"), 0.5)

    def test_ndcg_perfect_first(self):
        hits = [{"sourceUrl": "https://www.geostat.ge/ka/statistics"}]
        self.assertAlmostEqual(run_eval.ndcg_at_k(hits, "geostat.ge", 10), 1.0)

    def test_score_query_requires_min_chunks(self):
        hits = [{"sourceUrl": "https://www.geostat.ge/ka/statistics"}]
        scored = run_eval.score_query(hits, "geostat.ge", 3)
        self.assertEqual(scored["hit@5"], 0.0)
        self.assertEqual(scored["result_count"], 1)

    def test_aggregate_buckets_by_locale(self):
        rows = [
            {"locale": "ka", "hit@1": 1.0, "hit@5": 1.0, "MRR": 1.0, "NDCG@10": 1.0, "response_ms": 10.0},
            {"locale": "en", "hit@1": 0.0, "hit@5": 0.0, "MRR": 0.0, "NDCG@10": 0.0, "response_ms": 20.0},
        ]
        buckets = run_eval.aggregate_buckets(rows, "locale")
        self.assertAlmostEqual(buckets["ka"]["hit@1"], 1.0)
        self.assertAlmostEqual(buckets["en"]["hit@1"], 0.0)

    def test_entity_f1_overlap(self):
        expected = [{"normalizedForm": "inflation"}]
        actual = [{"normalizedForm": "Inflation"}, {"normalizedForm": "cpi"}]
        self.assertAlmostEqual(run_eval.entity_f1(expected, actual), 2 / 3)

    def test_intent_match(self):
        self.assertEqual(run_eval.intent_match("factual", "factual"), 1.0)
        self.assertEqual(run_eval.intent_match("lookup", "factual"), 0.0)

    def test_compare_baseline_reference_strict(self):
        metrics = {"hit@5": 0.84, "MRR": 0.65}
        reference = {"metrics": {"hit@5": 0.85, "MRR": 0.65}}
        failures = run_eval.compare_baseline(metrics, reference, 0.0)
        self.assertEqual(len(failures), 1)
        self.assertIn("hit@5", failures[0])

    def test_compare_baseline_reference_pass(self):
        metrics = {"hit@5": 0.86, "MRR": 0.66}
        reference = {"metrics": {"hit@5": 0.85, "MRR": 0.65}}
        failures = run_eval.compare_baseline(metrics, reference, 0.0)
        self.assertEqual(failures, [])


if __name__ == "__main__":
    raise SystemExit(unittest.main())
