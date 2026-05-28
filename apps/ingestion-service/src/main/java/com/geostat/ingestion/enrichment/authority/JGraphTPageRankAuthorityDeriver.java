package com.geostat.ingestion.enrichment.authority;

import com.geostat.ingestion.enrichment.pagekind.PageKindValues;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.DocumentLinkEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.DocumentLinkRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.enrichment.AuthorityDeriver;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class JGraphTPageRankAuthorityDeriver implements AuthorityDeriver {

    private static final Logger log = LoggerFactory.getLogger(JGraphTPageRankAuthorityDeriver.class);

    private final DocumentRepository documentRepository;
    private final DocumentLinkRepository documentLinkRepository;
    private final EnrichmentProperties properties;

    public JGraphTPageRankAuthorityDeriver(
            DocumentRepository documentRepository,
            DocumentLinkRepository documentLinkRepository,
            EnrichmentProperties properties) {
        this.documentRepository = documentRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void recomputeForCorpus(UUID corpusId) {
        List<DocumentEntity> documents =
                documentRepository.findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed);
        if (documents.isEmpty()) {
            return;
        }

        Set<UUID> eligible = new HashSet<>();
        for (DocumentEntity document : documents) {
            if (!PageKindValues.NAVIGATION.equals(document.getPageKind())) {
                eligible.add(document.getId());
            }
        }

        DefaultDirectedGraph<UUID, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        eligible.forEach(graph::addVertex);

        int edgeCount = 0;
        List<DocumentLinkEntity> edges = documentLinkRepository.findResolvedEdgesByCorpus(corpusId);
        for (DocumentLinkEntity link : edges) {
            UUID sourceId = link.getSourceDocId();
            UUID targetId = link.getTargetDocId();
            if (!eligible.contains(sourceId) || !eligible.contains(targetId)) {
                continue;
            }
            graph.addVertex(sourceId);
            graph.addVertex(targetId);
            if (!graph.containsEdge(sourceId, targetId)) {
                graph.addEdge(sourceId, targetId);
                edgeCount++;
            }
        }

        Map<UUID, Double> rawScores = computePageRank(graph, properties.pagerankDamping());
        Map<UUID, Double> normalized = AuthorityScoreComposer.normalizeMinMax(rawScores);
        Instant now = Instant.now();

        for (DocumentEntity document : documents) {
            double score;
            if (PageKindValues.NAVIGATION.equals(document.getPageKind())) {
                score = 0.0;
            } else {
                double pageRank = normalized.getOrDefault(document.getId(), 0.0);
                double freshness = FreshnessDecay.score(document.getFetchedAt(), now);
                score = AuthorityScoreComposer.compose(pageRank, freshness);
            }
            documentRepository.updateAuthorityScore(document.getId(), score);
        }

        log.info(
                "authority recomputed for corpus {} — docs={}, graphNodes={}, edges={}",
                corpusId,
                documents.size(),
                graph.vertexSet().size(),
                edgeCount);
    }

    static Map<UUID, Double> computePageRank(DefaultDirectedGraph<UUID, DefaultEdge> graph, double damping) {
        if (graph.vertexSet().isEmpty()) {
            return Map.of();
        }
        PageRank<UUID, DefaultEdge> pageRank = new PageRank<>(graph, damping);
        return pageRank.getScores();
    }
}
