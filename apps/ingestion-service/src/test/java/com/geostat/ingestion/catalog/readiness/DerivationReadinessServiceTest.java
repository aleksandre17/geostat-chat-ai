package com.geostat.ingestion.catalog.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.catalog.AggregationProperties;
import com.geostat.ingestion.catalog.refresh.CatalogViewStatusService;
import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.persistence.entity.EvaluationQueryEntity;
import com.geostat.ingestion.persistence.repository.CurationOverrideRepository;
import com.geostat.ingestion.persistence.repository.EvaluationQueryRepository;
import com.geostat.ingestion.quality.persistence.CorpusPipelineMetrics;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class DerivationReadinessServiceTest {

    @Mock
    private DerivationReadinessReader reader;

    @Mock
    private EvaluationQueryRepository evaluationQueryRepository;

    @Mock
    private CurationOverrideRepository curationOverrideRepository;

    @Mock
    private ObjectProvider<CatalogViewStatusService> catalogViewStatusServiceProvider;

    private EnrichmentProperties enrichmentProperties;
    private AggregationProperties aggregationProperties;
    private IngestionProperties ingestionProperties;
    private DerivationReadinessService service;

    @BeforeEach
    void setUp() {
        enrichmentProperties = new EnrichmentProperties();
        aggregationProperties = new AggregationProperties();
        ingestionProperties = new IngestionProperties(
                null,
                null,
                null,
                null,
                new IngestionProperties.QualityAudit(100, 0.15, 3, 0.85, 0.95, 10),
                null,
                null,
                null,
                null);
        service = new DerivationReadinessService(
                reader,
                evaluationQueryRepository,
                curationOverrideRepository,
                enrichmentProperties,
                aggregationProperties,
                ingestionProperties,
                catalogViewStatusServiceProvider);
    }

    @Test
    void yamlBaselineReadyWhenGoldenSetAndVectorsOk() {
        when(reader.corpusExists("geostat-portal")).thenReturn(true);
        when(evaluationQueryRepository.findByCorpusNameAndActiveTrueOrderByLocaleAscQueryTextAsc("geostat-portal"))
                .thenReturn(Collections.nCopies(150, new EvaluationQueryEntity()));
        when(curationOverrideRepository.countActive(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(0L);
        when(reader.loadEnrichmentMetrics("geostat-portal"))
                .thenReturn(new DerivationReadinessReader.EnrichmentMetrics(100, 0, 0, 0));
        when(reader.loadPipelineMetrics("geostat-portal"))
                .thenReturn(new CorpusPipelineMetrics(1000, 100, 950));
        when(reader.loadTopicClusterMetrics("geostat-portal"))
                .thenReturn(new DerivationReadinessReader.TopicClusterMetrics(0, 0));
        when(reader.countPortalLinks("geostat-portal")).thenReturn(0L);

        var report = service.assess("geostat-portal");

        assertThat(report.readyForEvalGate()).isTrue();
        assertThat(report.enrichmentEnabled()).isFalse();
    }

    @Test
    void derivationPathRequiresEnrichmentGates() {
        enrichmentProperties.setEnabled(true);
        aggregationProperties.setEnabled(true);

        when(reader.corpusExists("geostat-portal")).thenReturn(true);
        when(evaluationQueryRepository.findByCorpusNameAndActiveTrueOrderByLocaleAscQueryTextAsc("geostat-portal"))
                .thenReturn(Collections.nCopies(150, new EvaluationQueryEntity()));
        when(curationOverrideRepository.countActive(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(0L);
        when(reader.loadEnrichmentMetrics("geostat-portal"))
                .thenReturn(new DerivationReadinessReader.EnrichmentMetrics(100, 50, 50, 40));
        when(reader.loadPipelineMetrics("geostat-portal"))
                .thenReturn(new CorpusPipelineMetrics(1000, 100, 950));
        when(reader.loadTopicClusterMetrics("geostat-portal"))
                .thenReturn(new DerivationReadinessReader.TopicClusterMetrics(0, 5));
        when(reader.countPortalLinks("geostat-portal")).thenReturn(0L);

        var report = service.assess("geostat-portal");

        assertThat(report.readyForEvalGate()).isFalse();
        assertThat(report.checks().stream().filter(c -> !c.passed()).map(c -> c.id()).toList())
                .contains("summary_coverage", "topic_clusters_approved", "mv_portal_links");
    }
}
