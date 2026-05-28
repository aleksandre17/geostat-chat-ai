package com.geostat.ingestion.enrichment.topic;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.locale.VectorSimilarity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import com.geostat.platform.enrichment.TopicMiner;
import com.geostat.platform.enrichment.TopicMiningReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class SmileKMeansTopicMiner implements TopicMiner {

    private static final Logger log = LoggerFactory.getLogger(SmileKMeansTopicMiner.class);
    private static final int LABEL_SAMPLE_SIZE = 5;

    private final DocumentRepository documentRepository;
    private final TopicClusterRepository topicClusterRepository;
    private final CorpusRepository corpusRepository;
    private final SummaryEmbeddingSource embeddingSource;
    private final GeminiTopicClusterLabeler clusterLabeler;
    private final EnrichmentProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public SmileKMeansTopicMiner(
            DocumentRepository documentRepository,
            TopicClusterRepository topicClusterRepository,
            CorpusRepository corpusRepository,
            SummaryEmbeddingSource embeddingSource,
            GeminiTopicClusterLabeler clusterLabeler,
            EnrichmentProperties properties,
            JdbcTemplate jdbcTemplate) {
        this.documentRepository = documentRepository;
        this.topicClusterRepository = topicClusterRepository;
        this.corpusRepository = corpusRepository;
        this.embeddingSource = embeddingSource;
        this.clusterLabeler = clusterLabeler;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public TopicMiningReport remineForCorpus(UUID corpusId) {
        List<DocumentEntity> candidates = documentRepository.findTopicMiningCandidates(corpusId);
        int minDocuments = properties.topicMinDocuments();
        if (candidates.size() < minDocuments) {
            log.info(
                    "topic remine skipped for corpus {} — {} candidates < min {}",
                    corpusId,
                    candidates.size(),
                    minDocuments);
            return new TopicMiningReport(0, 0, candidates.size());
        }

        long approvedClusters = topicClusterRepository.countByCorpus_IdAndApprovedTrue(corpusId);
        if (approvedClusters > 0) {
            log.warn(
                    "topic remine for corpus {} replaces {} approved cluster(s) — admin re-approval required",
                    corpusId,
                    approvedClusters);
        }

        Map<UUID, float[]> embeddings = embeddingSource.embedDocuments(candidates);
        if (embeddings.size() < minDocuments) {
            return new TopicMiningReport(0, 0, candidates.size() - embeddings.size());
        }

        int clusterCount = TopicClusterCount.forDocumentCount(embeddings.size());
        if (clusterCount < 2) {
            return new TopicMiningReport(0, 0, candidates.size());
        }

        SmileKMeansEngine.ClusteringResult clustering = SmileKMeansEngine.cluster(embeddings, clusterCount);
        Map<Integer, List<UUID>> members = SmileKMeansEngine.groupMembers(clustering);
        Map<UUID, DocumentEntity> documentsById = indexDocuments(candidates);

        documentRepository.clearTopicClusterAssignments(corpusId);
        topicClusterRepository.deleteAllByCorpusId(corpusId);

        Map<UUID, UUID> documentToCluster = new HashMap<>();
        int clustersCreated = 0;
        CorpusEntity corpusRef = corpusRepository
                .findById(corpusId)
                .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusId));
        Set<String> usedLabelsKa = new HashSet<>();

        for (Map.Entry<Integer, List<UUID>> entry : members.entrySet()) {
            int clusterIndex = entry.getKey();
            List<UUID> memberIds = entry.getValue();
            if (memberIds.isEmpty()) {
                continue;
            }

            float[] centroid = clustering.centroids()[clusterIndex];
            List<DocumentEntity> sampleDocs =
                    pickSampleDocuments(memberIds, documentsById, embeddings, centroid);
            TopicClusterLabels labels = clusterLabeler.labelCluster(sampleDocs);

            TopicClusterEntity cluster = new TopicClusterEntity();
            cluster.setCorpus(corpusRef);
            cluster.setLabelKa(uniqueLabelKa(labels.labelKa(), clusterIndex, usedLabelsKa));
            cluster.setLabelEn(labels.labelEn());
            cluster.setKeywords(labels.keywords());
            cluster.setCentroidSummary(labels.centroidSummary());
            cluster.setCentroidEmbedding(centroid);
            cluster.setDocumentCount(memberIds.size());
            cluster.setApproved(false);
            topicClusterRepository.save(cluster);
            clustersCreated++;

            for (UUID documentId : memberIds) {
                documentToCluster.put(documentId, cluster.getId());
            }
        }

        int assigned = 0;
        if (!documentToCluster.isEmpty()) {
            List<Object[]> params = documentToCluster.entrySet().stream()
                    .map(e -> new Object[] {e.getValue(), e.getKey()})
                    .toList();
            jdbcTemplate.batchUpdate(
                    "UPDATE ingestion.document SET topic_cluster_id = ?, updated_at = NOW() WHERE id = ?",
                    params);
            assigned = documentToCluster.size();
        }

        log.info(
                "topic remine completed for corpus {} — clusters={}, assigned={}, candidates={}",
                corpusId,
                clustersCreated,
                assigned,
                candidates.size());
        return new TopicMiningReport(clustersCreated, assigned, candidates.size() - assigned);
    }

    static String uniqueLabelKa(String baseLabel, int clusterIndex, Set<String> usedLabelsKa) {
        String candidate = baseLabel != null && !baseLabel.isBlank() ? baseLabel.trim() : "თემა " + (clusterIndex + 1);
        if (usedLabelsKa.add(candidate)) {
            return candidate;
        }
        int suffix = 2;
        String unique = candidate + " (" + suffix + ")";
        while (!usedLabelsKa.add(unique)) {
            suffix++;
            unique = candidate + " (" + suffix + ")";
        }
        return unique;
    }

    private static Map<UUID, DocumentEntity> indexDocuments(List<DocumentEntity> candidates) {
        Map<UUID, DocumentEntity> byId = new HashMap<>();
        for (DocumentEntity document : candidates) {
            byId.put(document.getId(), document);
        }
        return byId;
    }

    private static List<DocumentEntity> pickSampleDocuments(
            List<UUID> memberIds,
            Map<UUID, DocumentEntity> documentsById,
            Map<UUID, float[]> embeddings,
            float[] centroid) {
        List<DocumentEntity> samples = new ArrayList<>();
        for (UUID memberId : memberIds) {
            DocumentEntity document = documentsById.get(memberId);
            float[] vector = embeddings.get(memberId);
            if (document != null && vector != null) {
                samples.add(document);
            }
        }
        return samples.stream()
                .sorted(Comparator.comparingDouble(
                        doc -> -VectorSimilarity.cosine(centroid, embeddings.get(doc.getId()))))
                .limit(LABEL_SAMPLE_SIZE)
                .toList();
    }
}
