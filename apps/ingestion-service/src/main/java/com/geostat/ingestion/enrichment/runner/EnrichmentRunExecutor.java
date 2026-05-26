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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class EnrichmentRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentRunExecutor.class);

    private final DocumentRepository documentRepository;
    private final EnrichmentRunRepository enrichmentRunRepository;

    public EnrichmentRunExecutor(
            DocumentRepository documentRepository, EnrichmentRunRepository enrichmentRunRepository) {
        this.documentRepository = documentRepository;
        this.enrichmentRunRepository = enrichmentRunRepository;
    }

    public <T> void run(
            UUID documentId,
            EnrichmentDeriverKind deriverKind,
            String modelVersion,
            int maxRetries,
            Predicate<DocumentEntity> skipWhen,
            Function<DocumentEntity, T> derive,
            BiConsumer<DocumentEntity, T> persist,
            String logLabel) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null || skipWhen.test(document)) {
            return;
        }
        if (enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
                documentId, deriverKind, modelVersion, EnrichmentRunStatus.completed)) {
            return;
        }

        EnrichmentRunEntity run = enrichmentRunRepository
                .findByDocument_IdAndDeriverKindAndModelVersion(documentId, deriverKind, modelVersion)
                .orElseGet(() -> newRun(document, deriverKind, modelVersion));

        if (run.getStatus() == EnrichmentRunStatus.completed) {
            return;
        }

        Instant started = Instant.now();
        run.setStatus(EnrichmentRunStatus.running);
        run.setStartedAt(started);
        run.setError(null);
        enrichmentRunRepository.save(run);

        try {
            T result = deriveWithRetry(document, maxRetries, derive);
            persist.accept(document, result);
            documentRepository.save(document);

            Instant finished = Instant.now();
            run.setStatus(EnrichmentRunStatus.completed);
            run.setFinishedAt(finished);
            run.setDurationMs((int) (finished.toEpochMilli() - started.toEpochMilli()));
            enrichmentRunRepository.save(run);
            log.debug("{} enrichment completed for document {}", logLabel, documentId);
        } catch (Exception e) {
            Instant finished = Instant.now();
            run.setStatus(EnrichmentRunStatus.failed);
            run.setFinishedAt(finished);
            run.setDurationMs((int) (finished.toEpochMilli() - started.toEpochMilli()));
            run.setError(truncate(e.getMessage(), 500));
            enrichmentRunRepository.save(run);
            log.warn("{} enrichment failed for document {}: {}", logLabel, documentId, e.getMessage());
        }
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
