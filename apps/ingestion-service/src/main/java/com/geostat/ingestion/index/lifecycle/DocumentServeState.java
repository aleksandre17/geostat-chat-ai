package com.geostat.ingestion.index.lifecycle;

/** Qdrant payload + retrieval citation gate (spec §17). */
public enum DocumentServeState {
    LIVE("live"),
    PARTIAL_ENRICHED("partial_enriched"),
    DROPPED("dropped");

    private final String payloadValue;

    DocumentServeState(String payloadValue) {
        this.payloadValue = payloadValue;
    }

    public String payloadValue() {
        return payloadValue;
    }
}
