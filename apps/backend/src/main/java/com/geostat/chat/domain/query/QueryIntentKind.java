package com.geostat.chat.domain.query;

/** Phase 8 query intent (RAG-U07c) — maps to eval harness `expected_intent`. */
public enum QueryIntentKind {
    FACTUAL,
    STATISTICAL,
    LOOKUP,
    COMPARE,
    DEFINITION,
    LATEST,
    NAVIGATION,
    SMALLTALK
}
