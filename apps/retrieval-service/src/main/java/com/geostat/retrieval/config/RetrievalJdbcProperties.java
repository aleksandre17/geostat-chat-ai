package com.geostat.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.retrieval.jdbc")
public record RetrievalJdbcProperties(String url, String username, String password, int maxPoolSize) {
    public RetrievalJdbcProperties {
        if (maxPoolSize <= 0) {
            maxPoolSize = 2;
        }
    }
}
