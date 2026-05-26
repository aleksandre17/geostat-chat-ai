package com.geostat.ingestion.enrichment.vectors;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.VectorsFactory.namedVectors;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.enrichment.DocumentVectorWriter;
import com.geostat.qdrant.QdrantOperationException;
import com.geostat.qdrant.VectorCollectionNaming;
import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.ChunkRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointVectors;
import io.qdrant.client.grpc.Points.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(
        prefix = "geostat.ingestion.enrichment",
        name = "named-vectors-enabled",
        havingValue = "true")
public class QdrantNamedVectorWriter implements DocumentVectorWriter {

    private static final Logger log = LoggerFactory.getLogger(QdrantNamedVectorWriter.class);

    private final QdrantClient client;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingPort embeddingPort;

    public QdrantNamedVectorWriter(
            QdrantClient client,
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            EmbeddingPort embeddingPort) {
        this.client = client;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingPort = embeddingPort;
    }

    @Override
    public void writeNamedVector(UUID documentId, String vectorName, float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (embedding.length != embeddingPort.dimensions()) {
            throw new IllegalArgumentException("embedding dimensions mismatch");
        }

        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null || document.getCorpus() == null) {
            return;
        }

        List<ChunkEntity> chunks = chunkRepository.findByDocument_IdOrderBySequenceNoAsc(documentId);
        if (chunks.isEmpty()) {
            log.debug("no indexed chunks yet for document {} — deferring named vector {}", documentId, vectorName);
            return;
        }

        String collectionName = VectorCollectionNaming.collectionForName(document.getCorpus().getName());
        List<Float> values = toFloatList(embedding);
        List<PointVectors> updates = new ArrayList<>(chunks.size());
        for (ChunkEntity chunk : chunks) {
            Vector vector = Vector.newBuilder().addAllData(values).build();
            updates.add(PointVectors.newBuilder()
                    .setId(id(chunk.getId()))
                    .setVectors(namedVectors(Map.of(vectorName, vector)))
                    .build());
        }

        try {
            client.updateVectorsAsync(collectionName, updates).get();
            log.debug(
                    "updated named vector {} on {} points for document {} in {}",
                    vectorName,
                    updates.size(),
                    documentId,
                    collectionName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted updating named vector " + vectorName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed updating named vector " + vectorName, e.getCause());
        }
    }

    private static List<Float> toFloatList(float[] embedding) {
        List<Float> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add(value);
        }
        return values;
    }
}
