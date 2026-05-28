package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.events.DocumentEnrichmentTrigger;
import com.geostat.platform.contracts.ingestion.DocumentParsedEvent;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnExpression(
        "${geostat.ingestion.enrichment.enabled:false} && ${geostat.ingestion.events.enabled:false}")
public class RabbitDocumentEnrichmentPublisher implements DocumentEnrichmentTrigger {

    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final IngestionProperties properties;

    public RabbitDocumentEnrichmentPublisher(
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate, IngestionProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void requestEnrichment(UUID documentId, UUID corpusId) {
        IngestionProperties.Events events = properties.events();
        DocumentParsedEvent event = new DocumentParsedEvent(documentId, corpusId);
        rabbitTemplate.convertAndSend(events.exchange(), events.enrichmentRoutingKey(), event);
    }
}
