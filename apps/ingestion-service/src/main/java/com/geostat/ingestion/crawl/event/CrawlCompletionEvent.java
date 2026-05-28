package com.geostat.ingestion.crawl.event;

import java.util.UUID;

/**
 * Published after a crawl run completes successfully.
 * Consumed by {@link CrawlCompletionListener} to trigger
 * enrichment backfill, authority recompute, MV refresh, and vector cleanup.
 */
public record CrawlCompletionEvent(UUID runId, UUID corpusId, String corpusName) {}
