package com.geostat.ingestion.enrichment.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrichmentBackfillServiceTest {

    private static final String CORPUS_NAME = "geostat-portal";
    private static final String SUMMARY_MODEL = "gemini-2.5-flash-lite@2026-05-25";
    private static final String PAGE_KIND_MODEL = "gemini-2.5-flash-lite-pagekind@2026-05-25";

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentEnrichmentOrchestrator enrichmentOrchestrator;

    @Mock
    private CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    @Mock
    private EnrichmentProperties enrichmentProperties;

    private EnrichmentBackfillService service;
    private UUID corpusId;

    @BeforeEach
    void setUp() {
        service = new EnrichmentBackfillService(
                corpusRepository,
                documentRepository,
                enrichmentOrchestrator,
                catalogRefreshAfterBatch,
                enrichmentProperties);
        corpusId = UUID.randomUUID();
        lenient().when(enrichmentProperties.modelVersion()).thenReturn(SUMMARY_MODEL);
        lenient().when(enrichmentProperties.pageKindModelVersion()).thenReturn(PAGE_KIND_MODEL);
    }

    private void stubCorpus() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName(CORPUS_NAME);
        when(corpusRepository.findByName(CORPUS_NAME)).thenReturn(Optional.of(corpus));
    }

    @Test
    void queueBackfill_unknownCorpus_throws() {
        when(corpusRepository.findByName("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.queueBackfill("missing", 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void queueBackfill_respectsLimit_andSetsRunningProgress() {
        stubCorpus();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(documentRepository.findIdsNeedingP1EnrichmentBackfill(corpusId, SUMMARY_MODEL, PAGE_KIND_MODEL))
                .thenReturn(List.of(id1, id2));

        int queued = service.queueBackfill(CORPUS_NAME, 1, true);

        assertThat(queued).isEqualTo(1);
        EnrichmentBackfillProgress progress = service.progress();
        assertThat(progress.status()).isEqualTo("running");
        assertThat(progress.corpusName()).isEqualTo(CORPUS_NAME);
        assertThat(progress.documentsTotal()).isEqualTo(1);
        assertThat(progress.documentsProcessed()).isZero();
        assertThat(progress.startedAt()).isNotNull();
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    void queueBackfill_onlyMissingEmpty_finishesImmediately() {
        stubCorpus();
        when(documentRepository.findIdsNeedingP1EnrichmentBackfill(corpusId, SUMMARY_MODEL, PAGE_KIND_MODEL))
                .thenReturn(List.of());

        int queued = service.queueBackfill(CORPUS_NAME, 0, true);

        assertThat(queued).isZero();
        assertThat(service.progress().status()).isEqualTo("finished");
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void queueBackfill_rejectsSecondJobWhileRunning() throws Exception {
        stubCorpus();
        UUID id1 = UUID.randomUUID();
        when(documentRepository.findIdsNeedingP1EnrichmentBackfill(corpusId, SUMMARY_MODEL, PAGE_KIND_MODEL))
                .thenReturn(List.of(id1));

        CountDownLatch holdWorker = new CountDownLatch(1);
        doAnswer(invocation -> {
                    holdWorker.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(enrichmentOrchestrator)
                .enrichDocumentForBackfill(id1);

        service.queueBackfill(CORPUS_NAME, 0, true);

        assertThatThrownBy(() -> service.queueBackfill(CORPUS_NAME, 0, true))
                .isInstanceOf(EnrichmentBackfillAlreadyRunningException.class);

        holdWorker.countDown();
    }

    @Test
    void queueBackfill_allParsedWhenOnlyMissingFalse() {
        stubCorpus();
        UUID id1 = UUID.randomUUID();
        when(documentRepository.findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed))
                .thenReturn(List.of(document(id1)));

        int queued = service.queueBackfill(CORPUS_NAME, 0, false);

        assertThat(queued).isEqualTo(1);
        verify(documentRepository).findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed);
        verify(documentRepository, never()).findIdsNeedingP1EnrichmentBackfill(any(), any(), any());
    }

    @Test
    void runBackfill_enrichesAllDocuments_andRefreshesCatalog() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-05-25T12:00:00Z");

        service.runBackfill(CORPUS_NAME, List.of(id1, id2), startedAt);

        verify(enrichmentOrchestrator).enrichDocumentForBackfill(id1);
        verify(enrichmentOrchestrator).enrichDocumentForBackfill(id2);
        verify(catalogRefreshAfterBatch).refreshIfConfigured("enrichment-backfill");
        EnrichmentBackfillProgress progress = service.progress();
        assertThat(progress.status()).isEqualTo("finished");
        assertThat(progress.documentsProcessed()).isEqualTo(2);
        assertThat(progress.finishedAt()).isNotNull();
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void runBackfill_continuesWhenSingleDocumentFails() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        doThrow(new RuntimeException("gemini timeout")).when(enrichmentOrchestrator).enrichDocumentForBackfill(id1);

        service.runBackfill(CORPUS_NAME, List.of(id1, id2), Instant.now());

        verify(enrichmentOrchestrator).enrichDocumentForBackfill(id2);
        assertThat(service.progress().documentsProcessed()).isEqualTo(1);
        assertThat(service.progress().status()).isEqualTo("finished");
    }

    @Test
    void runBackfill_emptyList_stillRefreshesCatalog() {
        service.runBackfill(CORPUS_NAME, List.of(), Instant.now());

        verify(enrichmentOrchestrator, never()).enrichDocumentForBackfill(any());
        verify(catalogRefreshAfterBatch).refreshIfConfigured(eq("enrichment-backfill"));
        assertThat(service.progress().documentsProcessed()).isZero();
    }

    private static DocumentEntity document(UUID id) {
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        return document;
    }
}
