package com.geostat.ingestion.persistence.model;

public enum DocumentFetchStatus {
    pending,
    fetched,
    parsed,
    failed,
    skipped
}
