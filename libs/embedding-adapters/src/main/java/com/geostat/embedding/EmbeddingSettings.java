package com.geostat.embedding;

public record EmbeddingSettings(
        String provider,
        String geminiApiKey,
        String geminiModel,
        String ollamaBaseUrl,
        String ollamaModel) {

    public static EmbeddingSettings hashDefaults() {
        return new EmbeddingSettings(EmbeddingProvider.HASH.id(), "", "", "http://127.0.0.1:11434", "nomic-embed-text");
    }
}
