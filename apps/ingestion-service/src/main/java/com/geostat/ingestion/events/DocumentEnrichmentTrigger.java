package com.geostat.ingestion.events;

import java.util.UUID;

/** Port: request async per-document enrichment (RAG-U01). */
public interface DocumentEnrichmentTrigger {

    void requestEnrichment(UUID documentId, UUID corpusId);
}
