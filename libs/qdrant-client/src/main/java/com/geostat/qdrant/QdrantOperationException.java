package com.geostat.qdrant;

public class QdrantOperationException extends RuntimeException {

    public QdrantOperationException(String message) {
        super(message);
    }

    public QdrantOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
