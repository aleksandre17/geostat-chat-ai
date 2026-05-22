package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.index.ChunkVectorIndexer;
import com.geostat.platform.contracts.ingestion.DocumentIndexEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "true")
public class DocumentIndexEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexEventListener.class);

    private final ChunkVectorIndexer chunkVectorIndexer;

    public DocumentIndexEventListener(ChunkVectorIndexer chunkVectorIndexer) {
        this.chunkVectorIndexer = chunkVectorIndexer;
    }

    @RabbitListener(queues = "${geostat.ingestion.events.index-queue}")
    public void onDocumentIndex(DocumentIndexEvent event) {
        log.debug("index event received for document {}", event.documentId());
        chunkVectorIndexer.indexDocument(event.documentId(), event.corpusId());
    }
}
