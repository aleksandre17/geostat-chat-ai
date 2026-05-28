package com.geostat.ingestion.parse.reparse;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.parse.profile.ParseProperties;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
public class CorpusReparseService {

    private static final Logger log = LoggerFactory.getLogger(CorpusReparseService.class);

    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;
    private final CorpusReparseWorker reparseWorker;
    private final ParseProperties parseProperties;
    private final ReparseProperties reparseProperties;
    private final AtomicBoolean reparseRunning = new AtomicBoolean(false);
    private final AtomicReference<CorpusReparseProgress> progress =
            new AtomicReference<>(CorpusReparseProgress.idle());

    public CorpusReparseService(
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository,
            CorpusReparseWorker reparseWorker,
            ParseProperties parseProperties,
            ReparseProperties reparseProperties) {
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
        this.reparseWorker = reparseWorker;
        this.parseProperties = parseProperties;
        this.reparseProperties = reparseProperties;
    }

    public CorpusReparseProgress progress() {
        return progress.get();
    }

    public int queueReparse(String corpusName, int limit) {
        if (!parseProperties.profile().enabled()) {
            throw new IllegalStateException(
                    "parse profile disabled — set geostat.ingestion.parse.profile.enabled=true");
        }
        if (!reparseRunning.compareAndSet(false, true)) {
            throw new CorpusReparseAlreadyRunningException(corpusName);
        }

        CorpusEntity corpus;
        try {
            corpus = corpusRepository
                    .findByName(corpusName)
                    .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusName));
        } catch (RuntimeException e) {
            reparseRunning.set(false);
            throw e;
        }

        List<UUID> documentIds =
                documentRepository.findIdsByCorpusIdAndFetchStatus(corpus.getId(), DocumentFetchStatus.parsed);
        if (limit > 0 && documentIds.size() > limit) {
            documentIds = documentIds.subList(0, limit);
        }

        Instant startedAt = Instant.now();
        if (documentIds.isEmpty()) {
            progress.set(CorpusReparseProgress.finished(corpusName, 0, 0, 0, 0, 0, startedAt, startedAt));
            reparseRunning.set(false);
            return 0;
        }

        progress.set(CorpusReparseProgress.running(corpusName, documentIds.size(), 0, 0, 0, startedAt));
        List<UUID> ids = List.copyOf(documentIds);
        CorpusEntity corpusRef = corpus;
        CompletableFuture.runAsync(() -> runReparse(corpusRef, ids, startedAt));
        return ids.size();
    }

    void runReparse(CorpusEntity corpus, List<UUID> documentIds, Instant startedAt) {
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        try {
            int threads = reparseProperties.reparseWorkerThreads();
            ExecutorService pool = Executors.newFixedThreadPool(
                    threads, r -> new Thread(r, "reparse-" + corpus.getName()));
            try {
                List<CompletableFuture<Void>> futures = documentIds.stream()
                        .map(documentId -> CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        CorpusReparseWorker.ReparseOutcome outcome =
                                                reparseWorker.reparseDocument(corpus, documentId);
                                        int done = processed.incrementAndGet();
                                        if (outcome == CorpusReparseWorker.ReparseOutcome.ACCEPTED) {
                                            accepted.incrementAndGet();
                                        } else {
                                            skipped.incrementAndGet();
                                        }
                                        progress.set(CorpusReparseProgress.running(
                                                corpus.getName(),
                                                documentIds.size(),
                                                done,
                                                accepted.get(),
                                                skipped.get(),
                                                startedAt));
                                    } catch (Exception e) {
                                        log.warn("Reparse failed document {}: {}", documentId, e.getMessage());
                                        int done = processed.incrementAndGet();
                                        failed.incrementAndGet();
                                        progress.set(CorpusReparseProgress.running(
                                                corpus.getName(),
                                                documentIds.size(),
                                                done,
                                                accepted.get(),
                                                skipped.get(),
                                                startedAt));
                                    }
                                },
                                pool))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                pool.shutdown();
            }
            progress.set(CorpusReparseProgress.finished(
                    corpus.getName(),
                    documentIds.size(),
                    processed.get(),
                    accepted.get(),
                    skipped.get(),
                    failed.get(),
                    startedAt,
                    Instant.now()));
        } finally {
            reparseRunning.set(false);
        }
    }
}
