package com.geostat.ingestion.index.qdrant;

public class QdrantOperationException extends RuntimeException {

    public QdrantOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
