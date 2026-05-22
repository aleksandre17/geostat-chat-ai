package com.geostat.embedding;

import com.geostat.embedding.gemini.GeminiEmbeddingModel;
import com.geostat.embedding.hash.HashEmbeddingModel;
import com.geostat.embedding.ollama.OllamaEmbeddingModel;

public final class EmbeddingFactory {

    private EmbeddingFactory() {}

    public static EmbeddingPort create(EmbeddingSettings settings) {
        return switch (EmbeddingProvider.from(settings.provider())) {
            case GEMINI -> new GeminiEmbeddingModel(settings.geminiApiKey(), settings.geminiModel());
            case OLLAMA -> new OllamaEmbeddingModel(settings.ollamaBaseUrl(), settings.ollamaModel());
            case HASH -> new HashEmbeddingModel();
        };
    }
}
