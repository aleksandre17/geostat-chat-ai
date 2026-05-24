package com.geostat.retrieval.config;

import com.geostat.qdrant.QdrantClients;
import io.qdrant.client.QdrantClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class QdrantClientConfiguration {

    @Bean(destroyMethod = "close")
    QdrantClient qdrantClient(RetrievalProperties properties) {
        RetrievalProperties.Qdrant qdrant = properties.qdrant();
        return QdrantClients.grpcClient(qdrant.url(), qdrant.grpcPort(), qdrant.useTls(), qdrant.apiKey());
    }
}
