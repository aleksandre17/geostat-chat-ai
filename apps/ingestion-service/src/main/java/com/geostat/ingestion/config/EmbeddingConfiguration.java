package com.geostat.ingestion.config;

import com.geostat.embedding.EmbeddingFactory;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.embedding.EmbeddingSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("db")
public class EmbeddingConfiguration {

    @Bean
    EmbeddingPort embeddingPort(IngestionProperties properties) {
        IngestionProperties.Embedding embedding = properties.embedding();
        return EmbeddingFactory.create(new EmbeddingSettings(
                embedding.provider(),
                embedding.geminiApiKey(),
                embedding.geminiModel(),
                embedding.ollamaBaseUrl(),
                embedding.ollamaModel()));
    }
}
