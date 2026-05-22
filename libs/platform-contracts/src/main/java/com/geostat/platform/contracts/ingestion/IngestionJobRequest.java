package com.geostat.platform.contracts.ingestion;

/**
 * Trigger crawl/index pipeline for a corpus.
 */
public record IngestionJobRequest(
        String corpusName,
        String seedUrl,
        boolean fullRecrawl
) {}
