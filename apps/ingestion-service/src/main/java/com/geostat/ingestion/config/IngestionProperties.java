package com.geostat.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ingestion")
public record IngestionProperties(
        Indexing indexing, Qdrant qdrant, Embedding embedding, Events events) {

    public record Indexing(boolean enabled, String indexVersion) {}

    public record Events(boolean enabled, String exchange, String indexQueue, String routingKey) {}

    public record Qdrant(String url, int grpcPort, boolean useTls, String apiKey) {}

    public record Embedding(
            String provider,
            String modelId,
            int vectorSize,
            String geminiApiKey,
            String geminiModel,
            String ollamaBaseUrl,
            String ollamaModel) {}
}
