package com.geostat.ingestion.events;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Invokes index + enrichment triggers after a document is persisted (RAG-U01). */
@Component
@Profile("db")
public class DocumentPostPersistPipeline {

    private final DocumentIndexTrigger documentIndexTrigger;
    private final DocumentEnrichmentTrigger documentEnrichmentTrigger;

    public DocumentPostPersistPipeline(
            DocumentIndexTrigger documentIndexTrigger, DocumentEnrichmentTrigger documentEnrichmentTrigger) {
        this.documentIndexTrigger = documentIndexTrigger;
        this.documentEnrichmentTrigger = documentEnrichmentTrigger;
    }

    public void afterDocumentPersisted(UUID documentId, UUID corpusId) {
        documentIndexTrigger.requestIndex(documentId, corpusId);
        documentEnrichmentTrigger.requestEnrichment(documentId, corpusId);
    }
}
