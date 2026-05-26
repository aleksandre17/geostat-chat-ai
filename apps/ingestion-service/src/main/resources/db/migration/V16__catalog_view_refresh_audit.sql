-- RAG-U02 — track MV refresh times for stale detection (spec §8 degraded modes)

CREATE TABLE IF NOT EXISTS ingestion.catalog_view_refresh (
    view_name    TEXT PRIMARY KEY,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
