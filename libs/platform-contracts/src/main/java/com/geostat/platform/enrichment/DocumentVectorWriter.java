package com.geostat.platform.enrichment;

import java.util.UUID;

/** Port — write document-level named vectors to the vector store (RAG-U01h). */
public interface DocumentVectorWriter {

    void writeNamedVector(UUID documentId, String vectorName, float[] embedding);
}
