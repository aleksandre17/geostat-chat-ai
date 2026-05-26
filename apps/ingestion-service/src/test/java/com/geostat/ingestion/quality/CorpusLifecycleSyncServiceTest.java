package com.geostat.ingestion.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.index.lifecycle.DocumentQdrantLifecycleSync;
import com.geostat.ingestion.index.lifecycle.DocumentServeState;
import com.geostat.ingestion.index.lifecycle.DocumentServeStateResolver;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CorpusLifecycleSyncServiceTest {

    @Mock
    private IngestionProperties properties;

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentServeStateResolver serveStateResolver;

    @Mock
    private DocumentQdrantLifecycleSync documentQdrantLifecycleSync;

    private CorpusLifecycleSyncService service;

    @BeforeEach
    void setUp() {
        service = new CorpusLifecycleSyncService(
                properties,
                corpusRepository,
                documentRepository,
                serveStateResolver,
                documentQdrantLifecycleSync);
    }

    @Test
    void syncProcessesParsedDocuments() {
        when(properties.indexing()).thenReturn(new IngestionProperties.Indexing(true, "v1"));

        UUID corpusId = UUID.randomUUID();
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName("geostat-portal");

        DocumentEntity live = new DocumentEntity();
        live.setId(UUID.randomUUID());
        live.setFetchStatus(DocumentFetchStatus.parsed);

        DocumentEntity dropped = new DocumentEntity();
        dropped.setId(UUID.randomUUID());
        dropped.setFetchStatus(DocumentFetchStatus.parsed);

        when(corpusRepository.findByName("geostat-portal")).thenReturn(Optional.of(corpus));
        when(documentRepository.findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed))
                .thenReturn(List.of(live, dropped));
        when(serveStateResolver.resolve(live)).thenReturn(DocumentServeState.LIVE);
        when(serveStateResolver.resolve(dropped)).thenReturn(DocumentServeState.DROPPED);

        var report = service.syncQdrantMetadata("geostat-portal");

        assertThat(report.documentsProcessed()).isEqualTo(2);
        assertThat(report.metadataUpdated()).isEqualTo(1);
        assertThat(report.droppedRemoved()).isEqualTo(1);
        verify(documentQdrantLifecycleSync).syncDocumentEntity(live);
        verify(documentQdrantLifecycleSync).syncDocumentEntity(dropped);
    }

    @Test
    void rejectsWhenIndexingDisabled() {
        when(properties.indexing()).thenReturn(new IngestionProperties.Indexing(false, "v1"));

        assertThatThrownBy(() -> service.syncQdrantMetadata("geostat-portal"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
