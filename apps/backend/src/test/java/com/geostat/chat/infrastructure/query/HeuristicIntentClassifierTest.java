package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.domain.query.QueryIntentKind;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class HeuristicIntentClassifierTest {

    private HeuristicIntentClassifier classifier;

    @BeforeEach
    void setUp() throws IOException {
        classifier = new HeuristicIntentClassifier(loadKeywordMap(), new KeywordMatcher());
    }

    @Test
    void detectsNavigationIntent() {
        assertThat(classifier.classify("სად ვნახო მშპ", "სად ვნახო მშპ", "ka"))
                .isEqualTo(QueryIntentKind.NAVIGATION);
    }

    @Test
    void defaultsToLookupForKeywords() {
        assertThat(classifier.classify("statistika", "statistika", "ka")).isEqualTo(QueryIntentKind.LOOKUP);
    }

    private static Map<QueryIntentKind, List<String>> loadKeywordMap() throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/intent-keywords.yaml");
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        IntentKeywordsFile file = yaml.readValue(resource.getInputStream(), IntentKeywordsFile.class);
        Map<QueryIntentKind, List<String>> map = new EnumMap<>(QueryIntentKind.class);
        if (file.intents() != null) {
            file.intents().forEach((key, values) -> map.put(QueryIntentKind.valueOf(key), values));
        }
        return map;
    }

    private record IntentKeywordsFile(Map<String, List<String>> intents) {}
}
