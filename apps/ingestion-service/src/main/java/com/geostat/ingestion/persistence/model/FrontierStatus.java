package com.geostat.ingestion.persistence.model;

public enum FrontierStatus {
    queued,
    fetching,
    done,
    skipped,
    failed
}
