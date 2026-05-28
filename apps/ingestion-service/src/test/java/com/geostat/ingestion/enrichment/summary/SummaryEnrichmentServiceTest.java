package com.geostat.ingestion.enrichment.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.geostat.platform.enrichment.SummaryDeriver;
import com.geostat.platform.enrichment.SummaryResult;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummaryEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private SummaryDeriver summaryDeriver;

    private SummaryEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setModelVersion("test-model@v1");
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new SummaryEnrichmentService(executor, summaryDeriver, documentRepository, properties);
    }

    @Test
    void enrichDocumentPersistsSummariesAndCompletedRun() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId, EnrichmentDeriverKind.summary, "test-model@v1", EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId, EnrichmentDeriverKind.summary, "test-model@v1"))
                .thenReturn(Optional.empty());
        when(summaryDeriver.derive(any(DocumentContext.class)))
                .thenReturn(new SummaryResult("ka summary", "en summary", "test-model@v1"));

        service.enrichDocument(documentId);

        assertThat(document.getSummaryKa()).isEqualTo("ka summary");
        assertThat(document.getSummaryEn()).isEqualTo("en summary");
        verify(documentRepository).updateSummary(documentId, "ka summary", "en summary");

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
                        documentId, EnrichmentDeriverKind.summary, "test-model@v1", EnrichmentRunStatus.completed))
                .thenReturn(true);

        service.enrichDocument(documentId);

        verify(summaryDeriver, never()).derive(any());
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
