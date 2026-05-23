package com.geostat.ingestion.events.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.geostat.ingestion.events.DocumentIndexTrigger;
import com.geostat.ingestion.index.ChunkVectorIndexer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"db", "hybrid-env"})
class DocumentIndexEventAsyncIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("geostat")
            .withUsername("geostat")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> postgres.getJdbcUrl() + "?currentSchema=ingestion");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("geostat.ingestion.events.enabled", () -> "true");
    }

    @Autowired
    private DocumentIndexTrigger documentIndexTrigger;

    @MockitoBean
    private ChunkVectorIndexer chunkVectorIndexer;

    @Test
    void publishIndexEventIsConsumedAsynchronously() throws InterruptedException {
        UUID documentId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();

        documentIndexTrigger.requestIndex(documentId, corpusId);

        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                verify(chunkVectorIndexer).indexDocument(documentId, corpusId);
                return;
            } catch (AssertionError ignored) {
                Thread.sleep(200);
            }
        }

        verify(chunkVectorIndexer).indexDocument(documentId, corpusId);
        assertThat(documentId).isNotNull();
    }
}
