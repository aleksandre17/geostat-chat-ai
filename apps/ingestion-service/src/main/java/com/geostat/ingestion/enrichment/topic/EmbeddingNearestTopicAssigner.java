package com.geostat.ingestion.enrichment.topic;

import com.geostat.ingestion.locale.VectorSimilarity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import com.geostat.platform.enrichment.TopicAssigner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class EmbeddingNearestTopicAssigner implements TopicAssigner {

    private final DocumentRepository documentRepository;
    private final TopicClusterRepository topicClusterRepository;
    private final SummaryEmbeddingSource embeddingSource;

    public EmbeddingNearestTopicAssigner(
            DocumentRepository documentRepository,
            TopicClusterRepository topicClusterRepository,
            SummaryEmbeddingSource embeddingSource) {
        this.documentRepository = documentRepository;
        this.topicClusterRepository = topicClusterRepository;
        this.embeddingSource = embeddingSource;
    }

    @Override
    @Transactional
    public Optional<UUID> assignNearest(UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null || document.getCorpus() == null) {
            return Optional.empty();
        }

        float[] vector = embeddingSource.embedDocument(document);
        if (vector.length == 0) {
            return Optional.empty();
        }

        List<TopicClusterEntity> clusters =
                topicClusterRepository.findByCorpus_IdOrderByLabelKaAsc(document.getCorpus().getId());
        if (clusters.isEmpty()) {
            return Optional.empty();
        }

        UUID bestClusterId = null;
        double bestScore = -1.0;
        for (TopicClusterEntity cluster : clusters) {
            float[] centroid = cluster.getCentroidEmbedding();
            if (centroid == null || centroid.length == 0) {
                continue;
            }
            double score = VectorSimilarity.cosine(vector, centroid);
            if (score > bestScore) {
                bestScore = score;
                bestClusterId = cluster.getId();
            }
        }

        if (bestClusterId == null) {
            return Optional.empty();
        }

        return Optional.of(bestClusterId);
    }
}
