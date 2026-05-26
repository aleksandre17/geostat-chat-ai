package com.geostat.ingestion.catalog.topic;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db")
public class TopicClusterAdminService {

    private final CorpusRepository corpusRepository;
    private final TopicClusterRepository topicClusterRepository;
    private final CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    public TopicClusterAdminService(
            CorpusRepository corpusRepository,
            TopicClusterRepository topicClusterRepository,
            CatalogRefreshAfterBatch catalogRefreshAfterBatch) {
        this.corpusRepository = corpusRepository;
        this.topicClusterRepository = topicClusterRepository;
        this.catalogRefreshAfterBatch = catalogRefreshAfterBatch;
    }

    public List<TopicClusterView> listForCorpus(String corpusName) {
        UUID corpusId = resolveCorpusId(corpusName);
        return topicClusterRepository.findByCorpus_IdOrderByLabelKaAsc(corpusId).stream()
                .map(TopicClusterView::from)
                .toList();
    }

    @Transactional
    public TopicClusterView approve(UUID clusterId, String approvedBy) {
        TopicClusterEntity entity = loadCluster(clusterId);
        entity.setApproved(true);
        entity.setApprovedBy(requireActor(approvedBy));
        entity.setApprovedAt(Instant.now());
        TopicClusterEntity saved = topicClusterRepository.save(entity);
        catalogRefreshAfterBatch.refreshIfConfigured("topic-approval");
        return TopicClusterView.from(saved);
    }

    @Transactional
    public TopicClusterView unapprove(UUID clusterId) {
        TopicClusterEntity entity = loadCluster(clusterId);
        entity.setApproved(false);
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        TopicClusterEntity saved = topicClusterRepository.save(entity);
        catalogRefreshAfterBatch.refreshIfConfigured("topic-unapproval");
        return TopicClusterView.from(saved);
    }

    private TopicClusterEntity loadCluster(UUID clusterId) {
        return topicClusterRepository
                .findById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("unknown topic cluster: " + clusterId));
    }

    private UUID resolveCorpusId(String corpusName) {
        return corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusName))
                .getId();
    }

    private static String requireActor(String approvedBy) {
        if (approvedBy == null || approvedBy.isBlank()) {
            return "admin";
        }
        return approvedBy.strip();
    }

    public record TopicClusterView(
            UUID id,
            String labelKa,
            String labelEn,
            List<String> keywords,
            int documentCount,
            boolean approved,
            String approvedBy,
            Instant approvedAt) {
        static TopicClusterView from(TopicClusterEntity entity) {
            return new TopicClusterView(
                    entity.getId(),
                    entity.getLabelKa(),
                    entity.getLabelEn(),
                    List.copyOf(entity.getKeywords()),
                    entity.getDocumentCount(),
                    entity.isApproved(),
                    entity.getApprovedBy(),
                    entity.getApprovedAt());
        }
    }
}
