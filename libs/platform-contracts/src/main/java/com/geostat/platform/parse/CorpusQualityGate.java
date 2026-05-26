package com.geostat.platform.parse;

/** Evaluates whether cleaned content is acceptable for chunk/index/enrichment. */
public interface CorpusQualityGate {

    enum Decision {
        ACCEPT,
        SKIP_TOO_SHORT,
        SKIP_BOILERPLATE_ONLY
    }

    Decision evaluate(CleanedDocument doc, QualityThresholds thresholds);
}
