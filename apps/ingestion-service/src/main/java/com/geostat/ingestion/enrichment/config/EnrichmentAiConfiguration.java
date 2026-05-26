package com.geostat.ingestion.enrichment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class EnrichmentAiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentAiConfiguration.class);

    @Bean
    ObjectMapper enrichmentObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    Client enrichmentGoogleGenAiClient(Environment environment) {
        String apiKey = environment.getProperty("spring.ai.google.genai.api-key", "");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY required when geostat.ingestion.enrichment.enabled=true");
        }
        String model = environment.getProperty(
                "spring.ai.google.genai.chat.options.model", "gemini-2.5-flash-lite");
        log.info("Enrichment Gemini chat model={}", model);
        return Client.builder().apiKey(apiKey).build();
    }

    @Bean
    ChatClient enrichmentChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
