package com.geostat.chat.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
    DataSource catalogDataSource(CatalogProperties properties) {
        if (properties.jdbcUrl() == null || properties.jdbcUrl().isBlank()) {
            throw new IllegalStateException(
                    "geostat.chat.catalog.jdbc-url required when geostat.chat.catalog.source=derived");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.jdbcUrl());
        if (properties.jdbcUser() != null && !properties.jdbcUser().isBlank()) {
            dataSource.setUsername(properties.jdbcUser());
        }
        if (properties.jdbcPassword() != null && !properties.jdbcPassword().isBlank()) {
            dataSource.setPassword(properties.jdbcPassword());
        }
        dataSource.setMaximumPoolSize(3);
        dataSource.setPoolName("catalog-mv");
        return dataSource;
    }

    @Bean
    @ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
    JdbcTemplate catalogJdbcTemplate(DataSource catalogDataSource) {
        return new JdbcTemplate(catalogDataSource);
    }
}
