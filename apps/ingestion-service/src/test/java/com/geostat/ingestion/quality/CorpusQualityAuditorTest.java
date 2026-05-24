package com.geostat.ingestion.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.quality.persistence.CorpusDocumentMetrics;
import com.geostat.ingestion.quality.persistence.CorpusPipelineMetrics;
import org.junit.jupiter.api.Test;

class CorpusQualityAuditorTest {

    private static final IngestionProperties.QualityAudit THRESHOLDS =
            IngestionProperties.QualityAudit.defaults();

    @Test
    void recommendOkWhenCoverageHealthy() {
        var docs = new CorpusDocumentMetrics(100, 80, 2, 4);
        var pipeline = new CorpusPipelineMetrics(400, 78, 395);

        var recommendations = CorpusQualityAuditor.recommend(docs, pipeline, THRESHOLDS);

        assertThat(recommendations).containsExactly(CorpusQualityRecommendation.OK);
    }

    @Test
    void recommendPlaywrightWhenEmptyBodyRateHigh() {
        var docs = new CorpusDocumentMetrics(50, 40, 0, 8);
        var pipeline = new CorpusPipelineMetrics(120, 40, 120);

        var recommendations = CorpusQualityAuditor.recommend(docs, pipeline, THRESHOLDS);

        assertThat(recommendations).contains(CorpusQualityRecommendation.CONSIDER_PLAYWRIGHT_P3_03B);
    }

    @Test
    void recommendReindexWhenVectorCoverageLow() {
        var docs = new CorpusDocumentMetrics(50, 40, 0, 1);
        var pipeline = new CorpusPipelineMetrics(200, 40, 120);

        var recommendations = CorpusQualityAuditor.recommend(docs, pipeline, THRESHOLDS);

        assertThat(recommendations).contains(CorpusQualityRecommendation.CONSIDER_REINDEX_OPS02);
    }

    @Test
    void recommendNoDataWhenNothingParsed() {
        var docs = new CorpusDocumentMetrics(0, 0, 0, 0);
        var pipeline = new CorpusPipelineMetrics(0, 0, 0);

        var recommendations = CorpusQualityAuditor.recommend(docs, pipeline, THRESHOLDS);

        assertThat(recommendations).containsExactly(CorpusQualityRecommendation.NO_DATA);
    }
}
