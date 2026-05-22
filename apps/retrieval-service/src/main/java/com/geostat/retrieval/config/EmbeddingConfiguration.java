package com.geostat.retrieval.config;

import com.geostat.embedding.EmbeddingFactory;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.embedding.EmbeddingSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfiguration {

    @Bean
    EmbeddingPort embeddingPort(RetrievalProperties properties) {
        RetrievalProperties.Embedding embedding = properties.embedding();
        return EmbeddingFactory.create(new EmbeddingSettings(
                embedding.provider(),
                embedding.geminiApiKey(),
                embedding.geminiModel(),
                embedding.ollamaBaseUrl(),
                embedding.ollamaModel()));
    }
}
