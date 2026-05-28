package com.geostat.ingestion.index.qdrant;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import com.geostat.qdrant.QdrantOperationException;
import com.geostat.ingestion.index.lifecycle.DocumentServeState;
import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.parse.SectionPathExtractor;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class QdrantVectorStore {

    private final QdrantClient client;

    public QdrantVectorStore(QdrantClient client) {
        this.client = client;
    }

    public void deleteByDocumentId(String collectionName, UUID documentId) {
        Filter filter = Filter.newBuilder()
                .addMust(matchKeyword("documentId", documentId.toString()))
                .build();
        try {
            client.deleteAsync(collectionName, filter).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted deleting points for document " + documentId, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed deleting points for document " + documentId, e.getCause());
        }
    }

    public void delete(String collectionName, List<String> chunkIds) {
        if (chunkIds.isEmpty()) {
            return;
        }
        List<PointId> pointIds = new ArrayList<>(chunkIds.size());
        for (String chunkId : chunkIds) {
            pointIds.add(id(UUID.fromString(chunkId)));
        }
        try {
            client.deleteAsync(collectionName, pointIds).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted deleting " + chunkIds.size() + " points", e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed deleting " + chunkIds.size() + " points", e.getCause());
        }
    }

    public void upsert(
            String collectionName,
            List<ChunkEntity> chunks,
            DocumentEntity document,
            CorpusEntity corpus,
            float[][] vectors,
            String indexVersion) {
        upsert(collectionName, chunks, document, corpus, vectors, indexVersion, DocumentServeState.LIVE);
    }

    public void upsert(
            String collectionName,
            List<ChunkEntity> chunks,
            DocumentEntity document,
            CorpusEntity corpus,
            float[][] vectors,
            String indexVersion,
            DocumentServeState serveState) {
        if (chunks.isEmpty()) {
            return;
        }
        List<PointStruct> points = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEntity chunk = chunks.get(i);
            points.add(buildPoint(chunk, document, corpus, vectors[i], indexVersion, serveState));
        }
        try {
            client.upsertAsync(collectionName, points).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted upserting points to " + collectionName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed upserting points to " + collectionName, e.getCause());
        }
    }

    public void syncDocumentMetadata(
            String collectionName, UUID documentId, DocumentEntity document, DocumentServeState serveState) {
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = enrichmentPayload(document, serveState);
        Filter filter = Filter.newBuilder()
                .addMust(matchKeyword("documentId", documentId.toString()))
                .build();
        try {
            client.setPayloadAsync(collectionName, payload, filter, true, null, Duration.ofSeconds(30))
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted syncing payload for document " + documentId, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed syncing payload for document " + documentId, e.getCause());
        }
    }

    private static PointStruct buildPoint(
            ChunkEntity chunk,
            DocumentEntity document,
            CorpusEntity corpus,
            float[] vector,
            String indexVersion,
            DocumentServeState serveState) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float v : vector) {
            values.add(v);
        }
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
        payload.put("documentId", value(document.getId().toString()));
        payload.put("corpusId", value(corpus.getId().toString()));
        payload.put("corpusName", value(corpus.getName()));
        payload.put("chunkId", value(chunk.getId().toString()));
        payload.put("sequenceNo", value(chunk.getSequenceNo()));
        payload.put("url", value(document.getCanonicalUrl()));
        payload.put("text", value(chunk.getText()));
        payload.put("chunkStrategy", value(chunk.getChunkStrategy() == null ? "" : chunk.getChunkStrategy()));
        if (document.getLanguage() != null) {
            payload.put("language", value(document.getLanguage()));
        }
        if (document.getTitle() != null) {
            payload.put("pageTitle", value(document.getTitle()));
        }
        if (document.getDisplayDescription() != null && !document.getDisplayDescription().isBlank()) {
            payload.put("pageDescription", value(document.getDisplayDescription()));
        }
        String sectionPath = SectionPathExtractor.joinPath(document.getSectionPath());
        if (!sectionPath.isBlank()) {
            payload.put("sectionPath", value(sectionPath));
        }
        if (chunk.getNavBreadcrumb() != null && !chunk.getNavBreadcrumb().isBlank()) {
            payload.put("navBreadcrumb", value(chunk.getNavBreadcrumb()));
        }
        if (document.getFetchedAt() != null) {
            payload.put("fetchedAt", value(document.getFetchedAt().toString()));
        }
        if (document.getPublishedAt() != null) {
            payload.put("publishedAt", value(document.getPublishedAt().toString()));
        }
        if (indexVersion != null && !indexVersion.isBlank()) {
            payload.put("indexVersion", value(indexVersion));
        }
        if (chunk.getEmbeddingModel() != null && !chunk.getEmbeddingModel().isBlank()) {
            payload.put("embeddingModel", value(chunk.getEmbeddingModel()));
        }
        if (document.getKeywords() != null && document.getKeywords().length > 0) {
            payload.put("keywords", value(String.join(" ", document.getKeywords())));
        }
        payload.putAll(enrichmentPayload(document, serveState));
        return PointStruct.newBuilder()
                .setId(id(chunk.getId()))
                .setVectors(vectors(values))
                .putAllPayload(payload)
                .build();
    }

    private static Map<String, io.qdrant.client.grpc.JsonWithInt.Value> enrichmentPayload(
            DocumentEntity document, DocumentServeState serveState) {
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
        payload.put("serveState", value(serveState.payloadValue()));
        payload.put("pageKind", value(document.getPageKind() == null ? "unknown" : document.getPageKind()));
        payload.put("scoreBoost", value(document.getScoreBoost()));
        return payload;
    }
}
