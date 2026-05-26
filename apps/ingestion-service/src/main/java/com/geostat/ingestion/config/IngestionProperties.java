package com.geostat.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ingestion")
public record IngestionProperties(
        Indexing indexing,
        Qdrant qdrant,
        Embedding embedding,
        Events events,
        QualityAudit qualityAudit,
        Scheduler scheduler,
        Playwright playwright,
        Archive archive,
        Freshness freshness) {

    public IngestionProperties {
        if (qualityAudit == null) {
            qualityAudit = QualityAudit.defaults();
        }
        if (scheduler == null) {
            scheduler = Scheduler.defaults();
        }
        if (playwright == null) {
            playwright = Playwright.defaults();
        }
        if (archive == null) {
            archive = Archive.defaults();
        }
        if (freshness == null) {
            freshness = Freshness.defaults();
        }
    }

    public record Indexing(boolean enabled, String indexVersion) {}

    public record Events(
            boolean enabled,
            String exchange,
            String indexQueue,
            String routingKey,
            String enrichmentQueue,
            String enrichmentRoutingKey) {

        public Events {
            if (enrichmentQueue == null || enrichmentQueue.isBlank()) {
                enrichmentQueue = "geostat.ingestion.document-parsed";
            }
            if (enrichmentRoutingKey == null || enrichmentRoutingKey.isBlank()) {
                enrichmentRoutingKey = "document.parsed";
            }
        }
    }

    /** B-26 — background crawl scheduler (Q-09). */
    public record Scheduler(boolean enabled, long fixedDelayMs) {
        public static Scheduler defaults() {
            return new Scheduler(false, 3_600_000L);
        }
    }

    public record Qdrant(String url, int grpcPort, boolean useTls, String apiKey) {}

    public record Embedding(
            String provider,
            String modelId,
            int vectorSize,
            String geminiApiKey,
            String geminiModel,
            String ollamaBaseUrl,
            String ollamaModel) {}

    /** P3-03b / OPS-02 thresholds — corpus-quality-audit (ADR-010). */
    public record QualityAudit(
            int minContentChars,
            double emptyBodyPlaywrightRate,
            int minEmptyBodyDocs,
            double chunkCoverageMin,
            double vectorCoverageMin,
            int minParsedDocsForReindex) {

        public static QualityAudit defaults() {
            return new QualityAudit(100, 0.15, 3, 0.85, 0.90, 10);
        }
    }

    /** P3-03b — Playwright SPA fetch (audit trigger only). */
    public record Playwright(boolean enabled, int timeoutMs) {
        public static Playwright defaults() {
            return new Playwright(false, 30_000);
        }
    }

    /** B-28 — raw HTML archive (noop or S3-compatible / MinIO). */
    public record Archive(
            boolean enabled,
            String provider,
            String endpoint,
            String bucket,
            String accessKey,
            String secretKey,
            String region) {

        public static Archive defaults() {
            return new Archive(false, "noop", "", "geostat-raw-html", "", "", "us-east-1");
        }
    }

    /** RAG freshness — incremental conditional re-fetch. */
    public record Freshness(boolean enabled, long staleAfterMs, int batchSize) {
        public static Freshness defaults() {
            return new Freshness(true, 86_400_000L, 50);
        }
    }
}
