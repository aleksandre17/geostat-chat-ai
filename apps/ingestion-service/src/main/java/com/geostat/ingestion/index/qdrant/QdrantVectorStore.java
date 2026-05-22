package com.geostat.ingestion.index.qdrant;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import java.util.ArrayList;
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

    public void upsert(
            String collectionName,
            List<ChunkEntity> chunks,
            DocumentEntity document,
            CorpusEntity corpus,
            float[][] vectors) {
        if (chunks.isEmpty()) {
            return;
        }
        List<PointStruct> points = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEntity chunk = chunks.get(i);
            points.add(buildPoint(chunk, document, corpus, vectors[i]));
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

    private static PointStruct buildPoint(
            ChunkEntity chunk, DocumentEntity document, CorpusEntity corpus, float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return PointStruct.newBuilder()
                .setId(id(chunk.getId()))
                .setVectors(vectors(values))
                .putAllPayload(Map.of(
                        "documentId", value(document.getId().toString()),
                        "corpusId", value(corpus.getId().toString()),
                        "corpusName", value(corpus.getName()),
                        "chunkId", value(chunk.getId().toString()),
                        "sequenceNo", value(chunk.getSequenceNo()),
                        "url", value(document.getCanonicalUrl()),
                        "text", value(chunk.getText()),
                        "chunkStrategy", value(chunk.getChunkStrategy() == null ? "" : chunk.getChunkStrategy())))
                .build();
    }
}
