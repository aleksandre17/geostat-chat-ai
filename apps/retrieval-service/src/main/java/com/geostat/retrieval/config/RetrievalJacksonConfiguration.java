package com.geostat.retrieval.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ObjectMapper for Redis retrieval cache JSON (RetrievedChunk list). */
@Configuration
public class RetrievalJacksonConfiguration {

    @Bean
    ObjectMapper retrievalObjectMapper() {
        return new ObjectMapper();
    }
}
