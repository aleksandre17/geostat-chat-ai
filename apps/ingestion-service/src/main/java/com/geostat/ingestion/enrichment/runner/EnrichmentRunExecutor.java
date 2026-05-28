package com.geostat.ingestion.enrichment.runner;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.EnrichmentRunRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class EnrichmentRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentRunExecutor.class);

    private final DocumentRepository documentRepository;
    private final EnrichmentRunRepository enrichmentRunRepository;

    private EnrichmentRunExecutor self;

    public EnrichmentRunExecutor(
            DocumentRepository documentRepository, EnrichmentRunRepository enrichmentRunRepository) {
        this.documentRepository = documentRepository;
        this.enrichmentRunRepository = enrichmentRunRepository;
    }

    @Autowired
    @Lazy
    void setSelf(EnrichmentRunExecutor self) {
        this.self = self;
    }

    record EnrichmentRunHandle<T>(EnrichmentRunEntity run, DocumentEntity document, Instant startedAt) {}

    public <T> void run(
            UUID documentId,
            EnrichmentDeriverKind deriverKind,
            String modelVersion,
            int maxRetries,
            Predicate<DocumentEntity> skipWhen,
            Function<DocumentEntity, T> derive,
            BiConsumer<DocumentEntity, T> persist,
            String logLabel) {
        EnrichmentRunHandle<T> handle = self.loadAndMarkRunning(documentId, deriverKind, modelVersion, skipWhen);
        if (handle == null) {
            return;
        }

        try {
            T result = deriveWithRetry(handle.document(), maxRetries, derive);
            self.saveResult(handle, result, persist, logLabel);
        } catch (Exception e) {
            self.markFailed(handle, e, logLabel);
        }
    }

    @Transactional
    <T> EnrichmentRunHandle<T> loadAndMarkRunning(
            UUID documentId,
            EnrichmentDeriverKind deriverKind,
            String modelVersion,
            Predicate<DocumentEntity> skipWhen) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null || skipWhen.test(document)) {
            return null;
        }
        // Force-initialize lazy proxies while inside the @Transactional boundary so
        // derivers can safely read these associations outside this transaction.
        Hibernate.initialize(document.getCorpus());
        Hibernate.initialize(document.getLocalePairDocument());
        if (enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                documentId, deriverKind, modelVersion, EnrichmentRunStatus.completed)) {
            return null;
        }

        EnrichmentRunEntity run = enrichmentRunRepository
                .findByDocument_IdAndDeriverKindAndModelVersion(documentId, deriverKind, modelVersion)
                .orElseGet(() -> newRun(document, deriverKind, modelVersion));

        if (run.getStatus() == EnrichmentRunStatus.completed) {
            return null;
        }

        Instant started = Instant.now();
        run.setStatus(EnrichmentRunStatus.running);
        run.setStartedAt(started);
        run.setError(null);
        enrichmentRunRepository.save(run);

        return new EnrichmentRunHandle<>(run, document, started);
    }

    @Transactional
    <T> void saveResult(
            EnrichmentRunHandle<T> handle,
            T result,
            BiConsumer<DocumentEntity, T> persist,
            String logLabel) {
        persist.accept(handle.document(), result);

        Instant finished = Instant.now();
        EnrichmentRunEntity run = handle.run();
        run.setStatus(EnrichmentRunStatus.completed);
        run.setFinishedAt(finished);
        run.setDurationMs((int) (finished.toEpochMilli() - handle.startedAt().toEpochMilli()));
        enrichmentRunRepository.save(run);
        log.debug("{} enrichment completed for document {}", logLabel, handle.document().getId());
    }

    @Transactional
    <T> void markFailed(EnrichmentRunHandle<T> handle, Exception e, String logLabel) {
        Instant finished = Instant.now();
        EnrichmentRunEntity run = handle.run();
        run.setStatus(EnrichmentRunStatus.failed);
        run.setFinishedAt(finished);
        run.setDurationMs((int) (finished.toEpochMilli() - handle.startedAt().toEpochMilli()));
        run.setError(truncate(e.getMessage(), 500));
        enrichmentRunRepository.save(run);
        log.warn(
                "{} enrichment failed for document {}: {}",
                logLabel,
                handle.document().getId(),
                e.getMessage());
    }

    private static <T> T deriveWithRetry(
            DocumentEntity document, int maxRetries, Function<DocumentEntity, T> derive) {
        int attempts = Math.max(1, maxRetries + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return derive.apply(document);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < attempts) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw last != null ? last : new IllegalStateException("derive failed");
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static EnrichmentRunEntity newRun(
            DocumentEntity document, EnrichmentDeriverKind deriverKind, String modelVersion) {
        EnrichmentRunEntity run = new EnrichmentRunEntity();
        run.setDocument(document);
        run.setDeriverKind(deriverKind);
        run.setModelVersion(modelVersion);
        run.setStatus(EnrichmentRunStatus.pending);
        return run;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
