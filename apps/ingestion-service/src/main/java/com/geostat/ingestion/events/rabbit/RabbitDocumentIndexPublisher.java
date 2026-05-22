package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.events.DocumentIndexTrigger;
import com.geostat.platform.contracts.ingestion.DocumentIndexEvent;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "true")
public class RabbitDocumentIndexPublisher implements DocumentIndexTrigger {

    private final RabbitTemplate rabbitTemplate;
    private final IngestionProperties properties;

    public RabbitDocumentIndexPublisher(RabbitTemplate rabbitTemplate, IngestionProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void requestIndex(UUID documentId, UUID corpusId) {
        IngestionProperties.Events events = properties.events();
        DocumentIndexEvent event = new DocumentIndexEvent(documentId, corpusId);
        rabbitTemplate.convertAndSend(events.exchange(), events.routingKey(), event);
    }
}
