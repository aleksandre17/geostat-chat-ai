package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.enrichment.runner.DocumentEnrichmentOrchestrator;
import com.geostat.platform.contracts.ingestion.DocumentParsedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "true")
public class DocumentEnrichmentListener {

    private final DocumentEnrichmentOrchestrator enrichmentOrchestrator;

    public DocumentEnrichmentListener(DocumentEnrichmentOrchestrator enrichmentOrchestrator) {
        this.enrichmentOrchestrator = enrichmentOrchestrator;
    }

    @RabbitListener(queues = "${geostat.ingestion.events.enrichment-queue}")
    public void onDocumentParsed(DocumentParsedEvent event) {
        enrichmentOrchestrator.enrichDocument(event.documentId());
    }
}
