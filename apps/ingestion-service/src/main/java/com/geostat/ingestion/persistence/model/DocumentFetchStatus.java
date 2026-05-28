package com.geostat.ingestion.persistence.model;

public enum DocumentFetchStatus {
    pending,
    fetched,
    parsing,
    parsed,
    failed,
    skipped,
    stale
}
