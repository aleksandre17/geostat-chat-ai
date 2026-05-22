package com.geostat.ingestion.events;

import com.geostat.ingestion.index.ChunkVectorIndexer;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.events", name = "enabled", havingValue = "false", matchIfMissing = true)
public class SyncDocumentIndexPublisher implements DocumentIndexTrigger {

    private final ChunkVectorIndexer chunkVectorIndexer;

    public SyncDocumentIndexPublisher(ChunkVectorIndexer chunkVectorIndexer) {
        this.chunkVectorIndexer = chunkVectorIndexer;
    }

    @Override
    public void requestIndex(UUID documentId, UUID corpusId) {
        chunkVectorIndexer.indexDocument(documentId, corpusId);
    }
}
