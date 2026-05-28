package com.geostat.ingestion.enrichment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.Entity;
import com.geostat.platform.enrichment.EntityDeriver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private EntityDeriver entityDeriver;

    private EntityEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setEntityModelVersion("gemini-2.0-flash-lite-entities@2026-05-25");
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new EntityEnrichmentService(executor, entityDeriver, documentRepository, properties);
    }

    @Test
    void enrichDocumentPersistsEntitiesAndCompletedRun() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.entities,
                        "gemini-2.0-flash-lite-entities@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId,
                        EnrichmentDeriverKind.entities,
                        "gemini-2.0-flash-lite-entities@2026-05-25"))
                .thenReturn(Optional.empty());
        when(entityDeriver.deriveEntities(any(DocumentContext.class)))
                .thenReturn(List.of(
                        new Entity("INDICATOR", "CPI", "CPI", 0.95),
                        new Entity("YEAR", "2024", "2024", 1.0)));

        service.enrichDocument(documentId);

        assertThat(document.getEntities()).hasSize(2);
        assertThat(document.getEntities().get(0)).containsEntry("type", "INDICATOR");
        assertThat(document.getEntities().get(1)).containsEntry("value", "2024");
        verify(documentRepository).updateEntities(eq(documentId), org.mockito.ArgumentMatchers.anyList());

        ArgumentCaptor<EnrichmentRunEntity> runCaptor = ArgumentCaptor.forClass(EnrichmentRunEntity.class);
        verify(enrichmentRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().stream().map(EnrichmentRunEntity::getStatus))
                .contains(EnrichmentRunStatus.completed);
    }

    @Test
    void enrichDocumentSkipsWhenAlreadyCompleted() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.entities,
                        "gemini-2.0-flash-lite-entities@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(true);

        service.enrichDocument(documentId);

        verify(entityDeriver, never()).deriveEntities(any());
    }

    private static DocumentEntity parsedDocument(UUID id) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        corpus.setName("geostat-portal");
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setCanonicalUrl("https://www.geostat.ge/ka/statistics");
        document.setUrlHash("hash");
        document.setTitle("Statistics");
        document.setLanguage("ka");
        document.setContentText("CPI increased in 2024.");
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
