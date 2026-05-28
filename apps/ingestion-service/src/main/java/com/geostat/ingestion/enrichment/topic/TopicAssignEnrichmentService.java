package com.geostat.ingestion.enrichment.topic;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import com.geostat.platform.enrichment.TopicAssigner;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class TopicAssignEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final TopicAssigner topicAssigner;
    private final TopicClusterRepository topicClusterRepository;
    private final DocumentRepository documentRepository;
    private final EnrichmentProperties properties;

    public TopicAssignEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            TopicAssigner topicAssigner,
            TopicClusterRepository topicClusterRepository,
            DocumentRepository documentRepository,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.topicAssigner = topicAssigner;
        this.topicClusterRepository = topicClusterRepository;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.topicAssignModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.topic_assign,
                modelVersion,
                properties.maxRetries(),
                this::shouldSkip,
                document -> topicAssigner.assignNearest(document.getId()).orElse(null),
                this::persistAssignment,
                "topic_assign");
    }

    private boolean shouldSkip(DocumentEntity document) {
        if (document.getCorpus() == null) {
            return true;
        }
        return topicClusterRepository.countByCorpus_Id(document.getCorpus().getId()) == 0;
    }

    private void persistAssignment(DocumentEntity document, UUID clusterId) {
        if (clusterId != null) {
            document.setTopicClusterId(clusterId);
            documentRepository.updateTopicCluster(document.getId(), clusterId);
        }
    }
}
