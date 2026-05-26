package com.geostat.ingestion.enrichment.vectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.embedding.EmbeddingPort;
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
import com.geostat.platform.enrichment.DocumentVectorWriter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummaryVectorEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private DocumentVectorWriter documentVectorWriter;

    private SummaryVectorEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setModelVersion("gemini-2.0-flash-lite@2026-05-25");
        properties.setSummaryVectorModelVersion("hash-v1-summary-v1@2026-05-25");
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new SummaryVectorEnrichmentService(
                executor, embeddingPort, documentVectorWriter, enrichmentRunRepository, properties);
    }

    @Test
    void enrichDocumentRequiresCompletedSummaryRun() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.summary,
                        "gemini-2.0-flash-lite@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(false);

        service.enrichDocument(documentId);

        verify(documentVectorWriter, never()).writeNamedVector(eq(documentId), eq(NamedVectorNames.SUMMARY), eq(new float[] {0.3f, 0.4f}));
    }

    @Test
    void enrichDocumentWritesSummaryNamedVectorAfterSummaryCompleted() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(embeddingPort.embed("ka summary")).thenReturn(new float[] {0.3f, 0.4f});
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.summary,
                        "gemini-2.0-flash-lite@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(true);
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.summary_vector,
                        "hash-v1-summary-v1@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId,
                        EnrichmentDeriverKind.summary_vector,
                        "hash-v1-summary-v1@2026-05-25"))
                .thenReturn(Optional.empty());

        service.enrichDocument(documentId);

        verify(documentVectorWriter)
                .writeNamedVector(documentId, NamedVectorNames.SUMMARY, new float[] {0.3f, 0.4f});

        ArgumentCaptor<EnrichmentRunEntity> runCaptor = ArgumentCaptor.forClass(EnrichmentRunEntity.class);
        verify(enrichmentRunRepository, org.mockito.Mockito.atLeastOnce()).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().stream().map(EnrichmentRunEntity::getStatus))
                .contains(EnrichmentRunStatus.completed);
    }

    private static DocumentEntity parsedDocument(UUID id) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setTitle("Statistics");
        document.setLanguage("ka");
        document.setSummaryKa("ka summary");
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
