package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Builds SymSpell unigram lexicon from terminology overlay + spell-dictionary.yaml. */
@Component
public class SpellDictionaryLoader {

    private static final Logger log = LoggerFactory.getLogger(SpellDictionaryLoader.class);
    private static final long DEFAULT_FREQUENCY = 100L;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private Map<String, Long> unigrams = Map.of();

    @PostConstruct
    void load() {
        Map<String, Long> merged = new HashMap<>();
        loadTerminologyOverlay(merged);
        loadSpellDictionary(merged);
        unigrams = Map.copyOf(merged);
        log.info("Loaded {} SymSpell unigrams", unigrams.size());
    }

    public Map<String, Long> unigrams() {
        return unigrams;
    }

    private void loadTerminologyOverlay(Map<String, Long> merged) {
        ClassPathResource resource = new ClassPathResource("catalog/terminology-overlay.yaml");
        if (!resource.exists()) {
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            TerminologyFile file = yamlMapper.readValue(input, TerminologyFile.class);
            if (file.entries() == null) {
                return;
            }
            for (TerminologyEntry entry : file.entries()) {
                addTokens(merged, entry.triggers());
                addPhraseTokens(merged, entry.ka());
                addPhraseTokens(merged, entry.en());
            }
        } catch (IOException e) {
            log.warn("Failed to load terminology overlay for spell dictionary: {}", e.getMessage());
        }
    }

    private void loadSpellDictionary(Map<String, Long> merged) {
        ClassPathResource resource = new ClassPathResource("catalog/spell-dictionary.yaml");
        if (!resource.exists()) {
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            SpellDictionaryFile file = yamlMapper.readValue(input, SpellDictionaryFile.class);
            if (file.words() == null) {
                return;
            }
            for (SpellWord word : file.words()) {
                if (word.word() != null && !word.word().isBlank()) {
                    merged.put(word.word().toLowerCase(Locale.ROOT), Math.max(word.frequency(), DEFAULT_FREQUENCY));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load spell dictionary: {}", e.getMessage());
        }
    }

    private static void addTokens(Map<String, Long> merged, List<String> tokens) {
        if (tokens == null) {
            return;
        }
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                merged.putIfAbsent(token.toLowerCase(Locale.ROOT), DEFAULT_FREQUENCY);
            }
        }
    }

    private static void addPhraseTokens(Map<String, Long> merged, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return;
        }
        for (String token : phrase.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!token.isBlank()) {
                merged.putIfAbsent(token, DEFAULT_FREQUENCY);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TerminologyFile(List<TerminologyEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class TerminologyEntry {
        private List<String> triggers = List.of();
        private String ka;
        private String en;

        List<String> triggers() {
            return triggers;
        }

        String ka() {
            return ka;
        }

        String en() {
            return en;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SpellDictionaryFile(List<SpellWord> words) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SpellWord(String word, long frequency) {}
}
