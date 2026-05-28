package com.geostat.ingestion.index.qdrant;

import com.geostat.qdrant.QdrantOperationException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.HnswConfigDiff;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.QuantizationConfig;
import io.qdrant.client.grpc.Collections.QuantizationType;
import io.qdrant.client.grpc.Collections.ScalarQuantization;
import io.qdrant.client.grpc.Collections.VectorParams;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class QdrantCollectionManager {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionManager.class);

    private final QdrantClient client;

    public QdrantCollectionManager(QdrantClient client) {
        this.client = client;
    }

    public void ensureCollection(String collectionName, int vectorSize) {
        Integer existingSize = existingVectorSize(collectionName);
        if (existingSize != null) {
            if (existingSize == vectorSize) {
                ensurePayloadIndexes(collectionName);
                return;
            }
            log.warn(
                    "Qdrant collection {} vector size {} != required {} — recreating (re-index after embedding change)",
                    collectionName,
                    existingSize,
                    vectorSize);
            deleteCollection(collectionName);
        }
        createCollection(collectionName, vectorSize);
        ensurePayloadIndexes(collectionName);
    }

    private void createCollection(String collectionName, int vectorSize) {
        try {
            HnswConfigDiff hnsw = HnswConfigDiff.newBuilder()
                    .setM(16)
                    .setEfConstruct(128)
                    .build();

            QuantizationConfig quantization = QuantizationConfig.newBuilder()
                    .setScalar(ScalarQuantization.newBuilder()
                            .setType(QuantizationType.Int8)
                            .setQuantile(0.99f)
                            .setAlwaysRam(true)
                            .build())
                    .build();

            client.createCollectionAsync(
                            collectionName,
                            VectorParams.newBuilder()
                                    .setSize(vectorSize)
                                    .setDistance(Distance.Cosine)
                                    .setHnswConfig(hnsw)
                                    .setQuantizationConfig(quantization)
                                    .build())
                    .get();
            log.info(
                    "[qdrant] created collection {} (dim={}, hnsw m={}, quantization=int8)",
                    collectionName,
                    vectorSize,
                    16);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted creating collection " + collectionName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed creating collection " + collectionName, e.getCause());
        }
    }

    private void ensurePayloadIndexes(String collectionName) {
        ensurePayloadIndex(collectionName, "language",      PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "pageKind",      PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "serveState",    PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "navBreadcrumb", PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "corpusName",    PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "keywords", PayloadSchemaType.Text);
    }

    private void ensurePayloadIndex(String collectionName, String field, PayloadSchemaType type) {
        try {
            client.createPayloadIndexAsync(collectionName, field, type, null, null, null, null)
                    .get();
            log.debug("[qdrant] payload index ensured: {}.{}", collectionName, field);
        } catch (Exception e) {
            log.debug("[qdrant] payload index {} on {} skipped: {}", field, collectionName, e.getMessage());
        }
    }

    private void deleteCollection(String collectionName) {
        try {
            client.deleteCollectionAsync(collectionName).get();
            log.info("deleted Qdrant collection {}", collectionName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted deleting collection " + collectionName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed deleting collection " + collectionName, e.getCause());
        }
    }

    private Integer existingVectorSize(String collectionName) {
        try {
            CollectionInfo info = client.getCollectionInfoAsync(collectionName).get();
            if (!info.getConfig().getParams().hasVectorsConfig()) {
                return null;
            }
            return (int)
                    info.getConfig().getParams().getVectorsConfig().getParams().getSize();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted checking collection " + collectionName, e);
        } catch (ExecutionException e) {
            return null;
        }
    }
}
