package com.geostat.ingestion.quality.persistence;

import java.time.Instant;
import java.util.Map;

public record LatestCrawlRunSnapshot(String status, Instant finishedAt, Map<String, Object> stats) {}
