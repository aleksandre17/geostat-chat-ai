package com.geostat.ingestion.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.net.URI;
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
        URI uri = URI.create(qdrant.url());
        String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
        QdrantGrpcClient.Builder builder =
                QdrantGrpcClient.newBuilder(host, qdrant.grpcPort(), qdrant.useTls());
        if (qdrant.apiKey() != null && !qdrant.apiKey().isBlank()) {
            builder.withApiKey(qdrant.apiKey());
        }
        return new QdrantClient(builder.build());
    }
}
