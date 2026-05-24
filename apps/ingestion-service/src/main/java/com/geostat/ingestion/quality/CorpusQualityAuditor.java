package com.geostat.ingestion.quality;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.quality.persistence.CorpusDocumentMetrics;
import com.geostat.ingestion.quality.persistence.CorpusPipelineMetrics;
import com.geostat.ingestion.quality.persistence.CorpusQualityReader;
import com.geostat.ingestion.quality.persistence.LatestCrawlRunSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class CorpusQualityAuditor {

    private static final int SAMPLE_URL_LIMIT = 10;

    private final CorpusQualityReader reader;
    private final IngestionProperties properties;

    public CorpusQualityAuditor(CorpusQualityReader reader, IngestionProperties properties) {
        this.reader = reader;
        this.properties = properties;
    }

    public CorpusQualityReport audit(String corpusName) {
        if (!reader.corpusExists(corpusName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Corpus not found: " + corpusName);
        }

        IngestionProperties.QualityAudit thresholds = properties.qualityAudit();
        CorpusDocumentMetrics documents =
                reader.loadDocumentMetrics(corpusName, thresholds.minContentChars());
        CorpusPipelineMetrics pipeline = reader.loadPipelineMetrics(corpusName);
        List<String> sampleEmptyUrls =
                reader.sampleEmptyBodyUrls(
                        corpusName, thresholds.minContentChars(), SAMPLE_URL_LIMIT);
        LatestCrawlRunSnapshot latestRun =
                reader.loadLatestCrawlRun(corpusName).orElse(null);

        List<CorpusQualityRecommendation> recommendations =
                recommend(documents, pipeline, thresholds);

        return CorpusQualityReport.build(
                corpusName,
                Instant.now(),
                thresholds,
                documents,
                pipeline,
                sampleEmptyUrls,
                latestRun,
                recommendations);
    }

    static List<CorpusQualityRecommendation> recommend(
            CorpusDocumentMetrics documents,
            CorpusPipelineMetrics pipeline,
            IngestionProperties.QualityAudit thresholds) {
        Set<CorpusQualityRecommendation> out = new LinkedHashSet<>();

        long parsed = documents.parsedDocuments();
        if (parsed == 0) {
            out.add(CorpusQualityRecommendation.NO_DATA);
            return List.copyOf(out);
        }

        double emptyBodyRate = rate(documents.emptyBodyDocuments(), parsed);
        if (documents.emptyBodyDocuments() >= thresholds.minEmptyBodyDocs()
                && emptyBodyRate >= thresholds.emptyBodyPlaywrightRate()) {
            out.add(CorpusQualityRecommendation.CONSIDER_PLAYWRIGHT_P3_03B);
        }

        if (parsed >= thresholds.minParsedDocsForReindex()) {
            double chunkCoverage = rate(pipeline.documentsWithChunks(), parsed);
            if (chunkCoverage < thresholds.chunkCoverageMin()) {
                out.add(CorpusQualityRecommendation.CONSIDER_RECRAWL_OPS02);
            }

            if (pipeline.totalChunks() > 0) {
                double vectorCoverage = rate(pipeline.indexedChunks(), pipeline.totalChunks());
                if (vectorCoverage < thresholds.vectorCoverageMin()) {
                    out.add(CorpusQualityRecommendation.CONSIDER_REINDEX_OPS02);
                }
            }
        }

        if (out.isEmpty()) {
            out.add(CorpusQualityRecommendation.OK);
        }
        return new ArrayList<>(out);
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (double) numerator / (double) denominator;
    }
}
