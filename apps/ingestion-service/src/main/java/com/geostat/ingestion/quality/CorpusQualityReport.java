package com.geostat.ingestion.quality;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.quality.persistence.CorpusDocumentMetrics;
import com.geostat.ingestion.quality.persistence.CorpusPipelineMetrics;
import com.geostat.ingestion.quality.persistence.LatestCrawlRunSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CorpusQualityReport(
        String corpusName,
        Instant auditedAt,
        ThresholdView thresholds,
        DocumentView documents,
        PipelineView pipeline,
        List<String> sampleEmptyUrls,
        CrawlRunView latestCrawlRun,
        List<CorpusQualityRecommendation> recommendations) {

    static CorpusQualityReport build(
            String corpusName,
            Instant auditedAt,
            IngestionProperties.QualityAudit thresholds,
            CorpusDocumentMetrics documents,
            CorpusPipelineMetrics pipeline,
            List<String> sampleEmptyUrls,
            LatestCrawlRunSnapshot latestRun,
            List<CorpusQualityRecommendation> recommendations) {
        long parsed = documents.parsedDocuments();
        return new CorpusQualityReport(
                corpusName,
                auditedAt,
                ThresholdView.from(thresholds),
                DocumentView.from(documents, parsed),
                PipelineView.from(pipeline, parsed),
                List.copyOf(sampleEmptyUrls),
                latestRun == null ? null : CrawlRunView.from(latestRun),
                List.copyOf(recommendations));
    }

    public record ThresholdView(
            int minContentChars,
            double emptyBodyPlaywrightRate,
            int minEmptyBodyDocs,
            double chunkCoverageMin,
            double vectorCoverageMin,
            int minParsedDocsForReindex) {

        static ThresholdView from(IngestionProperties.QualityAudit thresholds) {
            return new ThresholdView(
                    thresholds.minContentChars(),
                    thresholds.emptyBodyPlaywrightRate(),
                    thresholds.minEmptyBodyDocs(),
                    thresholds.chunkCoverageMin(),
                    thresholds.vectorCoverageMin(),
                    thresholds.minParsedDocsForReindex());
        }
    }

    public record DocumentView(
            long total,
            long parsed,
            long failed,
            long emptyBody,
            double emptyBodyRate) {

        static DocumentView from(CorpusDocumentMetrics metrics, long parsed) {
            double emptyRate = parsed <= 0 ? 0.0d : (double) metrics.emptyBodyDocuments() / parsed;
            return new DocumentView(
                    metrics.totalDocuments(),
                    metrics.parsedDocuments(),
                    metrics.failedDocuments(),
                    metrics.emptyBodyDocuments(),
                    round(emptyRate));
        }
    }

    public record PipelineView(
            long totalChunks,
            long documentsWithChunks,
            double chunkCoverageRate,
            long indexedChunks,
            double vectorCoverageRate) {

        static PipelineView from(CorpusPipelineMetrics metrics, long parsed) {
            double chunkRate =
                    parsed <= 0 ? 0.0d : (double) metrics.documentsWithChunks() / parsed;
            double vectorRate =
                    metrics.totalChunks() <= 0
                            ? 0.0d
                            : (double) metrics.indexedChunks() / metrics.totalChunks();
            return new PipelineView(
                    metrics.totalChunks(),
                    metrics.documentsWithChunks(),
                    round(chunkRate),
                    metrics.indexedChunks(),
                    round(vectorRate));
        }
    }

    public record CrawlRunView(String status, Instant finishedAt, Map<String, Object> stats) {
        static CrawlRunView from(LatestCrawlRunSnapshot snapshot) {
            return new CrawlRunView(snapshot.status(), snapshot.finishedAt(), snapshot.stats());
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }
}
