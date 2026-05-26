package com.geostat.platform.enrichment;

/** Closed-set page classification result (RAG-U01f). */
public record PageKindResult(String kind, double confidence, String modelVersion) {}
