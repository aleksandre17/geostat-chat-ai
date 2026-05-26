package com.geostat.ingestion.enrichment.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingNearestTopicAssignerTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TopicClusterRepository topicClusterRepository;

    @Mock
    private SummaryEmbeddingSource embeddingSource;

    private EmbeddingNearestTopicAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new EmbeddingNearestTopicAssigner(documentRepository, topicClusterRepository, embeddingSource);
    }

    @Test
    void assignNearestPicksClusterWithHighestCosineSimilarity() {
        UUID documentId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID clusterNear = UUID.randomUUID();
        UUID clusterFar = UUID.randomUUID();

        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);

        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setCorpus(corpus);

        TopicClusterEntity near = cluster(clusterNear, new float[] {1.0f, 0.0f});
        TopicClusterEntity far = cluster(clusterFar, new float[] {0.0f, 1.0f});

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(embeddingSource.embedDocument(document)).thenReturn(new float[] {0.95f, 0.05f});
        when(topicClusterRepository.findByCorpus_IdOrderByLabelKaAsc(corpusId)).thenReturn(List.of(far, near));

        Optional<UUID> assigned = assigner.assignNearest(documentId);

        assertThat(assigned).contains(clusterNear);
    }

    private static TopicClusterEntity cluster(UUID id, float[] centroid) {
        TopicClusterEntity cluster = new TopicClusterEntity();
        cluster.setId(id);
        cluster.setCentroidEmbedding(centroid);
        return cluster;
    }
}
