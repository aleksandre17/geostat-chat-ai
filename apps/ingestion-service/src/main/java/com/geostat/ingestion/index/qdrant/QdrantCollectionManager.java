package com.geostat.ingestion.index.qdrant;

import io.qdrant.client.QdrantClient;
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
        if (collectionExists(collectionName)) {
            return;
        }
        try {
            client.createCollectionAsync(
                            collectionName,
                            VectorParams.newBuilder()
                                    .setSize(vectorSize)
                                    .setDistance(Distance.Cosine)
                                    .build())
                    .get();
            log.info("created Qdrant collection {}", collectionName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted creating collection " + collectionName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed creating collection " + collectionName, e.getCause());
        }
    }

    private boolean collectionExists(String collectionName) {
        try {
            client.getCollectionInfoAsync(collectionName).get();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted checking collection " + collectionName, e);
        } catch (ExecutionException e) {
            return false;
        }
    }
}
