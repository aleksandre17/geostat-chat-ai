package com.geostat.ingestion.persistence.model;

public enum CrawlRunStatus {
    pending,
    running,
    completed,
    failed,
    cancelled
}
