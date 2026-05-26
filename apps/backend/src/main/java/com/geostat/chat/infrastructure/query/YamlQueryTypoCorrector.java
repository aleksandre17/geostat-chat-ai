package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Manifest-driven phrase corrections for Georgian/Latin query typos (RAG-U07a). */
@Component
public class YamlQueryTypoCorrector {

    private static final Logger log = LoggerFactory.getLogger(YamlQueryTypoCorrector.class);
    private static final String RESOURCE = "catalog/query-typo-corrections.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private List<Correction> corrections = List.of();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.warn("Query typo corrections missing at {}", RESOURCE);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            CorrectionsFile file = yamlMapper.readValue(input, CorrectionsFile.class);
            if (file.corrections() == null || file.corrections().isEmpty()) {
                corrections = List.of();
                return;
            }
            List<Correction> sorted = new ArrayList<>(file.corrections());
            sorted.sort(Comparator.comparingInt(c -> -c.from().length()));
            corrections = List.copyOf(sorted);
            log.info("Loaded {} query typo corrections", corrections.size());
        } catch (IOException e) {
            log.warn("Failed to load query typo corrections: {}", e.getMessage());
        }
    }

    String fix(String text, String locale) {
        if (text == null || text.isBlank() || corrections.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (Correction correction : corrections) {
            result = replaceIgnoreCase(result, correction.from(), correction.to());
        }
        return result;
    }

    private static String replaceIgnoreCase(String text, String from, String to) {
        if (from == null || from.isBlank() || !text.toLowerCase(Locale.ROOT).contains(from.toLowerCase(Locale.ROOT))) {
            return text;
        }
        String lowerHaystack = text.toLowerCase(Locale.ROOT);
        String lowerNeedle = from.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int hit = lowerHaystack.indexOf(lowerNeedle, index);
            if (hit < 0) {
                out.append(text, index, text.length());
                break;
            }
            out.append(text, index, hit).append(to);
            index = hit + from.length();
        }
        return out.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CorrectionsFile(List<Correction> corrections) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Correction(String from, String to) {}
}
