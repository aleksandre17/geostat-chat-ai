package com.geostat.ingestion.config;

import com.geostat.qdrant.QdrantClients;
import io.qdrant.client.QdrantClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("db")
@EnableConfigurationProperties(IngestionProperties.class)
public class QdrantClientConfiguration {

    @Bean(destroyMethod = "close")
    QdrantClient qdrantClient(IngestionProperties properties) {
        IngestionProperties.Qdrant qdrant = properties.qdrant();
        return QdrantClients.grpcClient(qdrant.url(), qdrant.grpcPort(), qdrant.useTls(), qdrant.apiKey());
    }
}
