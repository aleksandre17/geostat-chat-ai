-- Supports batch hash dedup query in LinkDiscoverer (PERF-10)
CREATE INDEX IF NOT EXISTS idx_url_frontier_crawl_run_hash
    ON ingestion.url_frontier (crawl_run_id, url_hash);
