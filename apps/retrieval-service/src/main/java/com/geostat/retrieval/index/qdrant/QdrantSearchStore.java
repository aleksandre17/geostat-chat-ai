package com.geostat.retrieval.index.qdrant;

import static io.qdrant.client.ConditionFactory.matchKeyword;

import com.geostat.qdrant.QdrantOperationException;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.search.CitationEligibilityFilter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QdrantSearchStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantSearchStore.class);

    private final QdrantClient client;

    public QdrantSearchStore(QdrantClient client) {
        this.client = client;
    }

    public List<RetrievedChunk> search(String collectionName, float[] queryVector, int limit) {
        return search(collectionName, queryVector, limit, null);
    }

    public List<RetrievedChunk> search(String collectionName, float[] queryVector, int limit, String locale) {
        List<Float> vector = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vector.add(v);
        }
        SearchPoints.Builder builder = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(vector)
                .setLimit(limit)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());
        Filter.Builder filterBuilder = Filter.newBuilder()
                .addMust(matchKeyword("serveState", "live"));
        if (locale != null && !locale.isBlank()) {
            filterBuilder.addShould(matchKeyword("language", locale.toLowerCase()));
        }
        builder.setFilter(filterBuilder.build());
        try {
            List<ScoredPoint> hits = client.searchAsync(builder.build()).get();
            if (hits.isEmpty() && locale != null && !locale.isBlank()) {
                return search(collectionName, queryVector, limit, null);
            }
            return hits.stream()
                    .filter(hit -> CitationEligibilityFilter.isCitable(
                            stringValue(hit.getPayloadMap(), "serveState"),
                            stringValue(hit.getPayloadMap(), "pageKind")))
                    .map(this::toRetrievedChunk)
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted searching collection " + collectionName, e);
        } catch (ExecutionException e) {
            log.warn("Qdrant search failed for collection {}: {}", collectionName, e.getMessage());
            throw new QdrantOperationException("failed searching collection " + collectionName, e.getCause());
        }
    }

    private RetrievedChunk toRetrievedChunk(ScoredPoint hit) {
        Map<String, Value> payload = hit.getPayloadMap();
        return new RetrievedChunk(
                stringValue(payload, "documentId"),
                stringValue(payload, "url"),
                stringValue(payload, "text"),
                hit.getScore(),
                emptyToNull(stringValue(payload, "language")),
                emptyToNull(stringValue(payload, "pageTitle")),
                emptyToNull(stringValue(payload, "sectionPath")),
                emptyToNull(stringValue(payload, "pageDescription")),
                emptyToNull(stringValue(payload, "fetchedAt")),
                emptyToNull(stringValue(payload, "navBreadcrumb")),
                emptyToNull(stringValue(payload, "publishedAt")));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String stringValue(Map<String, Value> payload, String key) {
        Value value = payload.get(key);
        if (value == null) {
            return "";
        }
        if (value.hasStringValue()) {
            return value.getStringValue();
        }
        return value.toString();
    }
}
