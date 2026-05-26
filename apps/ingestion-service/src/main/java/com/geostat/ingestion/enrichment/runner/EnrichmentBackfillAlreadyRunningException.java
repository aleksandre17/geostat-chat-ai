package com.geostat.ingestion.enrichment.runner;

/** Raised when a second async enrichment backfill is requested while one is in flight. */
public class EnrichmentBackfillAlreadyRunningException extends IllegalStateException {

    private final String corpusName;

    public EnrichmentBackfillAlreadyRunningException(String corpusName) {
        super("enrichment backfill already running for corpus: " + corpusName);
        this.corpusName = corpusName;
    }

    public String corpusName() {
        return corpusName;
    }
}
