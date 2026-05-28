package com.geostat.ingestion.crawl.runner;

import java.util.List;
import java.util.UUID;

public record RunConfig(
        UUID runId,
        UUID corpusId,
        String corpusName,
        int maxPages,
        int maxDepth,
        int rateLimitMs,
        List<String> allowedHosts,
        int workerThreads) {}
