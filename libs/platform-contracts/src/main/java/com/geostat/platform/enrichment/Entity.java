package com.geostat.platform.enrichment;

/** Domain entity extracted from a document (RAG-U01c). */
public record Entity(String type, String value, String normalizedForm, double confidence) {}
