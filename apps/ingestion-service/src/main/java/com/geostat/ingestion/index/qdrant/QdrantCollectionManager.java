package com.geostat.ingestion.index.qdrant;

import com.geostat.qdrant.QdrantOperationException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.Distance;
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
    }

    private void createCollection(String collectionName, int vectorSize) {
        try {
            client.createCollectionAsync(
                            collectionName,
                            VectorParams.newBuilder()
                                    .setSize(vectorSize)
                                    .setDistance(Distance.Cosine)
                                    .build())
                    .get();
            log.info("created Qdrant collection {} (size={})", collectionName, vectorSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted creating collection " + collectionName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed creating collection " + collectionName, e.getCause());
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
