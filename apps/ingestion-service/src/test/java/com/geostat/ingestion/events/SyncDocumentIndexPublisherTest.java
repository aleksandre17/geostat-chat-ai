package com.geostat.ingestion.events;

import static org.mockito.Mockito.verify;

import com.geostat.ingestion.index.ChunkVectorIndexer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncDocumentIndexPublisherTest {

    @Mock
    private ChunkVectorIndexer chunkVectorIndexer;

    @InjectMocks
    private SyncDocumentIndexPublisher publisher;

    @Test
    void requestIndexDelegatesToChunkVectorIndexer() {
        UUID documentId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();

        publisher.requestIndex(documentId, corpusId);

        verify(chunkVectorIndexer).indexDocument(documentId, corpusId);
    }
}
