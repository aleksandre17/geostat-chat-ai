package com.geostat.platform.contracts.ingestion;

/**
 * Async job handle (skeleton).
 */
public record IngestionJobStatus(
        String jobId,
        String state,
        String message
) {}
