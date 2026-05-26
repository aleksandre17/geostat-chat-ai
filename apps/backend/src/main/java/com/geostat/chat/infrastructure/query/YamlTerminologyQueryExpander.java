package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.query.QueryExpander;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** RAG-U07e — terminology overlay synonyms (no LLM expand yet). */
@Component
public class YamlTerminologyQueryExpander implements QueryExpander {

    private static final Logger log = LoggerFactory.getLogger(YamlTerminologyQueryExpander.class);
    private static final String RESOURCE = "catalog/terminology-overlay.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private List<TermEntry> entries = List.of();

    @PostConstruct
    void loadOverlay() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.warn("terminology overlay missing at {}", RESOURCE);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            OverlayFile file = yamlMapper.readValue(input, OverlayFile.class);
            entries = file.entries() == null ? List.of() : List.copyOf(file.entries());
            log.info("Loaded {} terminology overlay entries", entries.size());
        } catch (IOException e) {
            log.warn("Failed to load terminology overlay: {}", e.getMessage());
        }
    }

    @Override
    public List<String> expand(String normalized, String locale) {
        if (normalized == null || normalized.isBlank() || entries.isEmpty()) {
            return List.of();
        }
        String haystack = normalized.toLowerCase(Locale.ROOT);
        Set<String> expansions = new LinkedHashSet<>();
        for (TermEntry entry : entries) {
            if (entry.matches(haystack, locale)) {
                expansions.addAll(entry.expansionPhrases(locale));
            }
        }
        return List.copyOf(expansions);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OverlayFile(List<TermEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class TermEntry {
        private List<String> triggers = List.of();
        private String ka;
        private String en;

        boolean matches(String haystack, String locale) {
            for (String trigger : triggers) {
                if (trigger != null && haystack.contains(trigger.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }

        List<String> expansionPhrases(String locale) {
            List<String> phrases = new ArrayList<>();
            if (ka != null && !ka.isBlank()) {
                phrases.add(ka.strip());
            }
            if (en != null && !en.isBlank()) {
                phrases.add(en.strip());
            }
            return phrases;
        }

        public void setTriggers(List<String> triggers) {
            this.triggers = triggers != null ? triggers : List.of();
        }

        public void setKa(String ka) {
            this.ka = ka;
        }

        public void setEn(String en) {
            this.en = en;
        }
    }
}
