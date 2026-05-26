package com.geostat.ingestion.enrichment.runner;

import com.geostat.ingestion.events.DocumentEnrichmentTrigger;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "false", matchIfMissing = true)
public class AsyncDocumentEnrichmentTrigger implements DocumentEnrichmentTrigger {

    private final DocumentEnrichmentOrchestrator enrichmentOrchestrator;

    public AsyncDocumentEnrichmentTrigger(DocumentEnrichmentOrchestrator enrichmentOrchestrator) {
        this.enrichmentOrchestrator = enrichmentOrchestrator;
    }

    @Override
    @Async
    public void requestEnrichment(UUID documentId, UUID corpusId) {
        enrichmentOrchestrator.enrichDocument(documentId);
    }
}
