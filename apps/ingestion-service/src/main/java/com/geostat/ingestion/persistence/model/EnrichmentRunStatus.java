package com.geostat.ingestion.persistence.model;

public enum EnrichmentRunStatus {
    pending,
    running,
    completed,
    failed,
    skipped
}
