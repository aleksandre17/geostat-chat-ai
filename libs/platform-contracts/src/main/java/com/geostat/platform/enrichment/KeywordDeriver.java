package com.geostat.platform.enrichment;

import java.util.List;

/** Port — statistical keyword extraction (RAG-U01b). */
public interface KeywordDeriver {

    List<String> deriveKeywords(DocumentContext document, int topN);
}
