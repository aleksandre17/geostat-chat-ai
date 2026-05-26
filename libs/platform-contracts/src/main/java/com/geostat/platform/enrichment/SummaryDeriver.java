package com.geostat.platform.enrichment;

/** Port — Gemini-backed summary extraction (RAG-U01a). */
public interface SummaryDeriver {

    SummaryResult derive(DocumentContext document);
}
