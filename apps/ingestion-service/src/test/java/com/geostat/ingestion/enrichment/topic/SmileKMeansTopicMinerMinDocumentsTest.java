package com.geostat.ingestion.enrichment.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import com.geostat.platform.enrichment.TopicMiningReport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SmileKMeansTopicMinerMinDocumentsTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TopicClusterRepository topicClusterRepository;

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private SummaryEmbeddingSource embeddingSource;

    @Mock
    private GeminiTopicClusterLabeler clusterLabeler;

    @Mock
    private CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private EnrichmentProperties properties;
    private SmileKMeansTopicMiner miner;

    @BeforeEach
    void setUp() {
        properties = new EnrichmentProperties();
        properties.setTopicMinDocuments(50);
        miner = new SmileKMeansTopicMiner(
                documentRepository,
                topicClusterRepository,
                corpusRepository,
                embeddingSource,
                clusterLabeler,
                properties,
                jdbcTemplate);
    }

    @Test
    void remineSkipsWhenBelowConfiguredMinimum() {
        UUID corpusId = UUID.randomUUID();
        when(documentRepository.findTopicMiningCandidates(corpusId)).thenReturn(tenDocuments());

        TopicMiningReport report = miner.remineForCorpus(corpusId);

        assertThat(report.clustersCreated()).isZero();
        assertThat(report.documentsAssigned()).isZero();
        assertThat(report.documentsSkipped()).isEqualTo(10);
    }

    private static List<DocumentEntity> tenDocuments() {
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> {
                    DocumentEntity document = new DocumentEntity();
                    document.setId(UUID.randomUUID());
                    return document;
                })
                .toList();
    }
}
