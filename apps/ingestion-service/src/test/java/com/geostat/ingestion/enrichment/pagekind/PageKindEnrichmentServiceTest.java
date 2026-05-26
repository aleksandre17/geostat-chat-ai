package com.geostat.ingestion.enrichment.pagekind;

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
import com.geostat.platform.enrichment.PageKindClassifier;
import com.geostat.platform.enrichment.PageKindResult;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageKindEnrichmentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EnrichmentRunRepository enrichmentRunRepository;

    @Mock
    private PageKindClassifier pageKindClassifier;

    private PageKindEnrichmentService service;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setPageKindModelVersion("gemini-2.0-flash-lite-pagekind@2026-05-25");
        EnrichmentRunExecutor executor =
                new EnrichmentRunExecutor(documentRepository, enrichmentRunRepository);
        service = new PageKindEnrichmentService(executor, pageKindClassifier, properties);
    }

    @Test
    void enrichDocumentPersistsPageKindAndCompletedRun() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = parsedDocument(documentId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                        documentId,
                        EnrichmentDeriverKind.page_kind,
                        "gemini-2.0-flash-lite-pagekind@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(false);
        when(enrichmentRunRepository.findByDocument_IdAndDeriverKindAndModelVersion(
                        documentId,
                        EnrichmentDeriverKind.page_kind,
                        "gemini-2.0-flash-lite-pagekind@2026-05-25"))
                .thenReturn(Optional.empty());
        when(pageKindClassifier.classify(any(DocumentContext.class)))
                .thenReturn(new PageKindResult(PageKindValues.DATASET, 0.93, "v1"));

        service.enrichDocument(documentId);

        assertThat(document.getPageKind()).isEqualTo(PageKindValues.DATASET);
        verify(documentRepository).save(document);

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
                        EnrichmentDeriverKind.page_kind,
                        "gemini-2.0-flash-lite-pagekind@2026-05-25",
                        EnrichmentRunStatus.completed))
                .thenReturn(true);

        service.enrichDocument(documentId);

        verify(pageKindClassifier, never()).classify(any());
    }

    private static DocumentEntity parsedDocument(UUID id) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setCanonicalUrl("https://www.geostat.ge/ka/statistics/data.csv");
        document.setTitle("Data");
        document.setLanguage("ka");
        document.setContentText("CSV dataset");
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
