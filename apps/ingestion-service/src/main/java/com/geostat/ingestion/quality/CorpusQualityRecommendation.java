package com.geostat.ingestion.quality;

/** Data-driven follow-ups from corpus quality audit (ADR-010, OPS-02, P3-03b). */
public enum CorpusQualityRecommendation {
    OK,
    NO_DATA,
    CONSIDER_PLAYWRIGHT_P3_03B,
    CONSIDER_RECRAWL_OPS02,
    CONSIDER_REINDEX_OPS02
}
