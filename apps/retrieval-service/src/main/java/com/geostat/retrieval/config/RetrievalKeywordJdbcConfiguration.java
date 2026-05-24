package com.geostat.retrieval.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Optional PG read-only access for hybrid keyword search (RAG-L06). */
@Configuration
@ConditionalOnProperty(prefix = "geostat.retrieval.keyword", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RetrievalJdbcProperties.class)
public class RetrievalKeywordJdbcConfiguration {

    @Bean
    HikariDataSource retrievalKeywordDataSource(RetrievalJdbcProperties jdbc) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbc.url());
        ds.setUsername(jdbc.username());
        ds.setPassword(jdbc.password());
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(jdbc.maxPoolSize());
        ds.setPoolName("retrieval-keyword");
        return ds;
    }

    @Bean
    JdbcTemplate retrievalKeywordJdbcTemplate(DataSource retrievalKeywordDataSource) {
        return new JdbcTemplate(retrievalKeywordDataSource);
    }
}
