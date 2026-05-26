package com.geostat.ingestion.events.rabbit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.platform.contracts.ingestion.DocumentIndexEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitDocumentIndexPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private IngestionProperties properties;

    @InjectMocks
    private RabbitDocumentIndexPublisher publisher;

    @BeforeEach
    void setUp() {
        whenEventsConfigured();
    }

    @Test
    void requestIndexPublishesDocumentIndexEvent() {
        UUID documentId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();

        publisher.requestIndex(documentId, corpusId);

        verify(rabbitTemplate)
                .convertAndSend(
                        eq("geostat.ingestion"),
                        eq("document.index"),
                        eq(new DocumentIndexEvent(documentId, corpusId)));
    }

    private void whenEventsConfigured() {
        org.mockito.Mockito.when(properties.events())
                .thenReturn(new IngestionProperties.Events(
                        true,
                        "geostat.ingestion",
                        "geostat.ingestion.document-index",
                        "document.index",
                        "geostat.ingestion.document-parsed",
                        "document.parsed"));
    }
}
