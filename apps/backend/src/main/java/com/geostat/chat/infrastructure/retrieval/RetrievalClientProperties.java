package com.geostat.chat.infrastructure.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.retrieval")
public record RetrievalClientProperties(
        boolean enabled,
        String baseUrl,
        String defaultCorpus,
        int maxChunks,
        int clarificationMaxChunks) {}
