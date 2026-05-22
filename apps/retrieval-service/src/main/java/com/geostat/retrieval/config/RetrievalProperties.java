package com.geostat.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.retrieval")
public record RetrievalProperties(String defaultCollection, Qdrant qdrant, Embedding embedding) {

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
