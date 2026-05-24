package com.geostat.qdrant;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.net.URI;

public final class QdrantClients {

    private QdrantClients() {}

    public static QdrantClient grpcClient(String url, int grpcPort, boolean useTls, String apiKey) {
        URI uri = URI.create(url);
        String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, grpcPort, useTls);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.withApiKey(apiKey);
        }
        return new QdrantClient(builder.build());
    }
}
