package com.geostat.retrieval.index.qdrant;

import static io.qdrant.client.ValueFactory.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.ScoredPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QdrantSearchStoreTest {

    @Mock
    private QdrantClient client;

    private QdrantSearchStore store;

    @BeforeEach
    void setUp() {
        store = new QdrantSearchStore(client);
    }

    @Test
    void toRetrievedChunk_populatesNavBreadcrumb_whenPresent() {
        ScoredPoint hit = scoredPoint(basePayload("სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა", null));
        when(client.searchAsync(any())).thenReturn(Futures.immediateFuture(List.of(hit)));

        List<RetrievedChunk> results = store.search("test-collection", new float[] {0.1f}, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).navBreadcrumb()).isEqualTo("სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა");
    }

    @Test
    void toRetrievedChunk_setsNavBreadcrumbNull_whenAbsentFromPayload() {
        ScoredPoint hit = scoredPoint(basePayload(null, null));
        when(client.searchAsync(any())).thenReturn(Futures.immediateFuture(List.of(hit)));

        List<RetrievedChunk> results = store.search("test-collection", new float[] {0.1f}, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).navBreadcrumb()).isNull();
    }

    @Test
    void toRetrievedChunk_populatesPublishedAt_whenPresent() {
        ScoredPoint hit = scoredPoint(basePayload(null, "2025-03-15T10:00:00+04:00"));
        when(client.searchAsync(any())).thenReturn(Futures.immediateFuture(List.of(hit)));

        List<RetrievedChunk> results = store.search("test-collection", new float[] {0.1f}, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).publishedAt()).isEqualTo("2025-03-15T10:00:00+04:00");
    }

    private static ScoredPoint scoredPoint(Map<String, Value> payload) {
        return ScoredPoint.newBuilder()
                .setScore(0.9f)
                .putAllPayload(payload)
                .build();
    }

    private static Map<String, Value> basePayload(String navBreadcrumb, String publishedAt) {
        Map<String, Value> payload = new HashMap<>();
        payload.put("documentId", value("doc-1"));
        payload.put("url", value("https://example.com/page"));
        payload.put("text", value("chunk text"));
        payload.put("serveState", value("live"));
        payload.put("pageKind", value("report"));
        if (navBreadcrumb != null) {
            payload.put("navBreadcrumb", value(navBreadcrumb));
        }
        if (publishedAt != null) {
            payload.put("publishedAt", value(publishedAt));
        }
        return payload;
    }
}
