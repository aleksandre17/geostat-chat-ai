package com.geostat.platform.enrichment;

/** Port — closed-set page kind classification (RAG-U01f). */
public interface PageKindClassifier {

    PageKindResult classify(DocumentContext document);
}
