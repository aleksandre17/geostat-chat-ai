package com.geostat.retrieval.search.hybrid;

import static io.qdrant.client.ConditionFactory.matchKeyword;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.NamedVectorSearchPort;
import com.geostat.qdrant.QdrantOperationException;
import com.geostat.qdrant.VectorCollectionNaming;
import com.geostat.retrieval.config.RetrievalProperties;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "geostat.retrieval.hybrid",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class QdrantNamedVectorSearchAdapter implements NamedVectorSearchPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantNamedVectorSearchAdapter.class);

    private final QdrantClient client;
    private final RetrievalProperties properties;

    public QdrantNamedVectorSearchAdapter(QdrantClient client, RetrievalProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<RetrievedChunk> search(
            String corpus, String vectorName, float[] queryVector, String locale, int topK) {

        String collection = resolveCollection(corpus);
        List<Float> vector = toFloatList(queryVector);

        SearchPoints.Builder builder = SearchPoints.newBuilder()
                .setCollectionName(collection)
                .setVectorName(vectorName)
                .addAllVector(vector)
                .setLimit(topK)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());

        if (locale != null && !locale.isBlank()) {
            builder.setFilter(Filter.newBuilder()
                    .addShould(matchKeyword("language", locale.toLowerCase()))
                    .build());
        }

        try {
            List<ScoredPoint> hits = client.searchAsync(builder.build()).get();
            return hits.stream()
                    .filter(hit -> CitationEligibilityFilter.isCitable(
                            stringValue(hit.getPayloadMap(), "serveState"),
                            stringValue(hit.getPayloadMap(), "pageKind")))
                    .map(this::toRetrievedChunk)
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException(
                    "interrupted searching " + vectorName + " in " + collection, e);
        } catch (ExecutionException e) {
            log.warn("Qdrant named search failed for {}/{}: {}", collection, vectorName, e.getMessage());
            return List.of();
        }
    }

    private String resolveCollection(String corpus) {
        if (corpus != null && !corpus.isBlank()) {
            return VectorCollectionNaming.collectionForName(corpus);
        }
        return properties.defaultCollection();
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
                emptyToNull(stringValue(payload, "fetchedAt")));
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String stringValue(Map<String, Value> payload, String key) {
        Value value = payload.get(key);
        if (value == null) return "";
        if (value.hasStringValue()) return value.getStringValue();
        return value.toString();
    }
}
