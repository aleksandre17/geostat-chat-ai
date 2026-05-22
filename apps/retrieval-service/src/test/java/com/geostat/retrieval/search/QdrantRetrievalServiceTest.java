package com.geostat.retrieval.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.config.RetrievalProperties;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.retrieval.index.qdrant.QdrantSearchStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QdrantRetrievalServiceTest {

    @Mock
    private RetrievalProperties properties;

    @Mock
    private EmbeddingPort embedding;

    @Mock
    private QdrantSearchStore searchStore;

    @InjectMocks
    private QdrantRetrievalService service;

    @BeforeEach
    void setUp() {
        when(properties.defaultCollection()).thenReturn("geostat-portal");
        when(embedding.embed("unemployment rate")).thenReturn(new float[] {0.1f, 0.2f});
    }

    @Test
    void searchesDefaultCollection() {
        when(searchStore.search(eq("geostat-portal"), any(), eq(5)))
                .thenReturn(List.of(new RetrievedChunk("doc-1", "https://example.com", "chunk text", 0.9)));

        List<RetrievedChunk> results =
                service.search(new RetrievalQuery("unemployment rate", "ka", 5));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).text()).isEqualTo("chunk text");
        verify(searchStore).search(eq("geostat-portal"), any(), eq(5));
    }

    @Test
    void usesCorpusNameWhenProvided() {
        when(embedding.embed("gdp")).thenReturn(new float[] {0.3f, 0.4f});
        when(searchStore.search(eq("geostat-portal"), any(), eq(3))).thenReturn(List.of());

        service.search(new RetrievalQuery("gdp", "ka", 3, "geostat-portal"));

        verify(searchStore).search(eq("geostat-portal"), any(), eq(3));
    }

    @Test
    void returnsEmptyForBlankQuery() {
        assertThat(service.search(new RetrievalQuery("  ", "ka", 5))).isEmpty();
    }
}
