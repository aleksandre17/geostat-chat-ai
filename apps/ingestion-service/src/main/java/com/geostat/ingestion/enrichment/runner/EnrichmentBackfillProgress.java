package com.geostat.ingestion.enrichment.runner;

import java.time.Instant;

/** In-memory progress for async corpus enrichment backfill (P1 cutover ops). */
public record EnrichmentBackfillProgress(
        String corpusName,
        String status,
        int documentsTotal,
        int documentsProcessed,
        Instant startedAt,
        Instant finishedAt) {

    public static EnrichmentBackfillProgress running(String corpusName, int total, int processed, Instant startedAt) {
        return new EnrichmentBackfillProgress(corpusName, "running", total, processed, startedAt, null);
    }

    public static EnrichmentBackfillProgress finished(
            String corpusName, int total, int processed, Instant startedAt, Instant finishedAt) {
        return new EnrichmentBackfillProgress(corpusName, "finished", total, processed, startedAt, finishedAt);
    }

    public static EnrichmentBackfillProgress idle() {
        return new EnrichmentBackfillProgress(null, "idle", 0, 0, null, null);
    }
}
