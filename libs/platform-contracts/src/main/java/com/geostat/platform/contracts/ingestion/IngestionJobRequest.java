package com.geostat.platform.contracts.ingestion;

import com.geostat.platform.crawl.RenderMode;

/**
 * Trigger crawl/index pipeline for a corpus.
 */
public record IngestionJobRequest(
        String corpusName,
        String seedUrl,
        boolean fullRecrawl,
        RenderMode renderMode
) {
    public IngestionJobRequest(String corpusName, String seedUrl, boolean fullRecrawl) {
        this(corpusName, seedUrl, fullRecrawl, null);
    }
}
