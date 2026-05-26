package com.geostat.ingestion.events;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDocumentEnrichmentTrigger implements DocumentEnrichmentTrigger {

    @Override
    public void requestEnrichment(UUID documentId, UUID corpusId) {
        // enrichment disabled
    }
}
