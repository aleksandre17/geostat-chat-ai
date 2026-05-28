package com.geostat.ingestion;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"db", "hybrid-env"})
class FlywayMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("geostat")
            .withUsername("geostat")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                postgres.getJdbcUrl() + "?currentSchema=ingestion");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void migrationsApplyAndSeedCorpus() {
        assertThat(flyway.info().all()).hasSizeGreaterThanOrEqualTo(2);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT name, status FROM ingestion.corpus ORDER BY name");

        assertThat(rows)
                .extracting(row -> row.get("name"))
                .contains("agriculture-ge", "geostat-portal");

        assertThat(rows)
                .filteredOn(row -> "agriculture-ge".equals(row.get("name")))
                .extracting(row -> row.get("status"))
                .containsExactly("active");
    }
}
