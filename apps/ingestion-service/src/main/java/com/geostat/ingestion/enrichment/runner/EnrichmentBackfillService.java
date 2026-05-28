package com.geostat.ingestion.enrichment.runner;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Batch enrichment for parsed corpus documents (P1 cutover backfill). */
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class EnrichmentBackfillService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentBackfillService.class);

    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;
    private final DocumentEnrichmentOrchestrator enrichmentOrchestrator;
    private final CatalogRefreshAfterBatch catalogRefreshAfterBatch;
    private final EnrichmentProperties enrichmentProperties;
    private final AtomicReference<EnrichmentBackfillProgress> progress =
            new AtomicReference<>(EnrichmentBackfillProgress.idle());
    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);

    public EnrichmentBackfillService(
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository,
            DocumentEnrichmentOrchestrator enrichmentOrchestrator,
            CatalogRefreshAfterBatch catalogRefreshAfterBatch,
            EnrichmentProperties enrichmentProperties) {
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
        this.enrichmentOrchestrator = enrichmentOrchestrator;
        this.catalogRefreshAfterBatch = catalogRefreshAfterBatch;
        this.enrichmentProperties = enrichmentProperties;
    }

    public EnrichmentBackfillProgress progress() {
        return progress.get();
    }

    public boolean isRunning() {
        return backfillRunning.get();
    }

    public int queueBackfill(String corpusName, int limit, boolean onlyMissing) {
        if (!backfillRunning.compareAndSet(false, true)) {
            throw new EnrichmentBackfillAlreadyRunningException(corpusName);
        }

        UUID corpusId;
        try {
            corpusId = corpusRepository
                    .findByName(corpusName)
                    .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusName))
                    .getId();
        } catch (RuntimeException e) {
            backfillRunning.set(false);
            throw e;
        }

        List<UUID> documentIds = resolveDocumentIds(corpusId, onlyMissing);
        if (limit > 0 && documentIds.size() > limit) {
            documentIds = documentIds.subList(0, limit);
        }

        Instant startedAt = Instant.now();
        if (documentIds.isEmpty()) {
            progress.set(EnrichmentBackfillProgress.finished(corpusName, 0, 0, startedAt, startedAt));
            backfillRunning.set(false);
            log.info("Enrichment backfill skipped corpus={} — no documents to process (onlyMissing={})", corpusName, onlyMissing);
            return 0;
        }

        progress.set(EnrichmentBackfillProgress.running(corpusName, documentIds.size(), 0, startedAt));
        List<UUID> ids = List.copyOf(documentIds);
        CompletableFuture.runAsync(() -> runBackfill(corpusName, ids, startedAt));
        return ids.size();
    }

    private List<UUID> resolveDocumentIds(UUID corpusId, boolean onlyMissing) {
        if (onlyMissing) {
            return documentRepository.findIdsNeedingP1EnrichmentBackfill(
                    corpusId, enrichmentProperties.modelVersion(), enrichmentProperties.pageKindModelVersion());
        }
        return documentRepository.findIdsByCorpusIdAndFetchStatus(corpusId, DocumentFetchStatus.parsed);
    }

    void runBackfill(String corpusName, List<UUID> documentIds, Instant startedAt) {
        try {
            log.info(
                    "Enrichment backfill started corpus={} documents={} (gate derivers only, entities skipped)",
                    corpusName,
                    documentIds.size());
            int threads = enrichmentProperties.backfillWorkerThreads();
            ExecutorService pool = Executors.newFixedThreadPool(
                    threads, r -> new Thread(r, "enrich-backfill-" + corpusName));
            AtomicInteger processed = new AtomicInteger(0);
            try {
                List<CompletableFuture<Void>> futures = documentIds.stream()
                        .map(documentId -> CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        enrichmentOrchestrator.enrichDocumentForBackfill(documentId);
                                        int done = processed.incrementAndGet();
                                        progress.set(EnrichmentBackfillProgress.running(
                                                corpusName, documentIds.size(), done, startedAt));
                                    } catch (Exception e) {
                                        log.warn(
                                                "Enrichment backfill failed document {}: {}",
                                                documentId,
                                                e.getMessage());
                                    }
                                },
                                pool))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                pool.shutdown();
            }
            int processedCount = processed.get();
            catalogRefreshAfterBatch.refreshIfConfigured("enrichment-backfill");
            Instant finishedAt = Instant.now();
            progress.set(EnrichmentBackfillProgress.finished(
                    corpusName, documentIds.size(), processedCount, startedAt, finishedAt));
            log.info(
                    "Enrichment backfill finished corpus={} processed={}/{}",
                    corpusName,
                    processedCount,
                    documentIds.size());
        } finally {
            backfillRunning.set(false);
        }
    }
}
