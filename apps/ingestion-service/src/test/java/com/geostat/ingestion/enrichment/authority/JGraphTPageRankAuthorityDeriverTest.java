package com.geostat.ingestion.enrichment.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.enrichment.pagekind.PageKindValues;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.DocumentLinkRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JGraphTPageRankAuthorityDeriverTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentLinkRepository documentLinkRepository;

    private EnrichmentProperties properties;
    private JGraphTPageRankAuthorityDeriver deriver;

    @BeforeEach
    void setUp() {
        properties = new EnrichmentProperties();
        properties.setPagerankDamping(0.85);
        deriver = new JGraphTPageRankAuthorityDeriver(documentRepository, documentLinkRepository, properties);
    }

    @Test
    void computePageRankRanksHubHigherThanLeaf() {
        UUID hub = UUID.randomUUID();
        UUID leafA = UUID.randomUUID();
        UUID leafB = UUID.randomUUID();

        DefaultDirectedGraph<UUID, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        graph.addVertex(hub);
        graph.addVertex(leafA);
        graph.addVertex(leafB);
        graph.addEdge(hub, leafA);
        graph.addEdge(hub, leafB);
        graph.addEdge(leafA, hub);
        graph.addEdge(leafB, hub);

        Map<UUID, Double> scores = JGraphTPageRankAuthorityDeriver.computePageRank(graph, 0.85);

        assertThat(scores.get(hub)).isGreaterThan(scores.get(leafA));
        assertThat(scores.get(hub)).isGreaterThan(scores.get(leafB));
    }

    @Test
    void recomputeForCorpusSetsNavigationToZeroAndPersistsScores() {
        UUID corpusId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID navId = UUID.randomUUID();

        DocumentEntity content = document("https://example.com/ka/report", contentId, "article", Instant.now());
        DocumentEntity navigation = document("https://example.com/ka/menu", navId, PageKindValues.NAVIGATION, Instant.now());

        when(documentRepository.findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed))
                .thenReturn(List.of(content, navigation));
        when(documentLinkRepository.findResolvedEdgesByCorpus(corpusId)).thenReturn(List.of());

        deriver.recomputeForCorpus(corpusId);

        verify(documentRepository).updateAuthorityScore(navId, 0.0);
        verify(documentRepository).updateAuthorityScore(eq(contentId), org.mockito.ArgumentMatchers.anyDouble());
    }

    private static DocumentEntity document(String url, UUID id, String pageKind, Instant fetchedAt) {
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCanonicalUrl(url);
        document.setUrlHash(com.geostat.platform.url.UrlHasher.hash(url));
        document.setPageKind(pageKind);
        document.setFetchedAt(fetchedAt);
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
