package com.geostat.embedding;

public enum EmbeddingProvider {
    HASH("hash-v1"),
    GEMINI("gemini"),
    OLLAMA("ollama");

    private final String id;

    EmbeddingProvider(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static EmbeddingProvider from(String value) {
        if (value == null || value.isBlank()) {
            return HASH;
        }
        String normalized = value.trim().toLowerCase();
        for (EmbeddingProvider provider : values()) {
            if (provider.id.equals(normalized)) {
                return provider;
            }
        }
        return HASH;
    }
}
