package com.geostat.ingestion.enrichment.keyword;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads Georgian/English stopwords from YAML.
 * Stopwords are filtered from YAKE keyword output before persistence to prevent
 * grammatical particles and function words polluting cluster matching.
 */
@Component
public class GeorgianStopwordLoader {

    private static final Logger log = LoggerFactory.getLogger(GeorgianStopwordLoader.class);
    private static final String RESOURCE = "catalog/georgian-stopwords.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private Set<String> stopwords = Set.of();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.warn("Georgian stopwords YAML missing at {}", RESOURCE);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            StopwordsFile file = yamlMapper.readValue(input, StopwordsFile.class);
            if (file.stopwords() == null) {
                stopwords = Set.of();
                return;
            }
            Set<String> combined = file.stopwords().values().stream()
                    .flatMap(List::stream)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            stopwords = Set.copyOf(combined);
            log.info("Loaded {} stopwords from {}", stopwords.size(), RESOURCE);
        } catch (IOException e) {
            log.warn("Failed to load Georgian stopwords: {}", e.getMessage());
        }
    }

    /** Returns all stopwords (lowercased). Thread-safe after @PostConstruct. */
    public Set<String> stopwords() {
        return stopwords;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StopwordsFile(Map<String, List<String>> stopwords) {}
}
