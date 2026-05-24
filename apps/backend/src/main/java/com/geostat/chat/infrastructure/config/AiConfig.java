package com.geostat.chat.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI Configuration for Gemini.
 *
 * proxyBeanMethods=false: prevents CGLIB proxying, avoids early-instantiation
 * issues that break @Value injection on @Bean method parameters.
 */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * Explicitly creates the GenAI client with API key.
     * Overrides the autoconfiguration bean (@ConditionalOnMissingBean)
     * so that Vertex AI mode (which requires ADC credentials) is never triggered.
     *
     * Environment is injected as a method parameter (not a field) to ensure
     * it is available even when the configuration class is instantiated early.
     */
    @Bean
    public Client googleGenAiClient(Environment environment) {
        String apiKey = environment.getProperty("spring.ai.google.genai.api-key", "");
        log.debug("Gemini client initialized, key length: {}", apiKey.length());
        return Client.builder().apiKey(apiKey).build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public ChatClient chatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}