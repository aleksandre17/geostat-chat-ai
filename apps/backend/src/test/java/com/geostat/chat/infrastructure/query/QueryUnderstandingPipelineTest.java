package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.application.query.QueryUnderstandingPipeline;
import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.domain.query.QueryIntentKind;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class QueryUnderstandingPipelineTest {

    private QueryUnderstandingPipeline pipeline;

    @BeforeEach
    void setUp() throws Exception {
        QueryUnderstandingProperties properties = new QueryUnderstandingProperties();
        YamlTerminologyQueryExpander terminology = new YamlTerminologyQueryExpander();
        terminology.loadOverlay();
        GeminiQueryExpander geminiExpander = mock(GeminiQueryExpander.class);
        when(geminiExpander.expand(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.List.of());

        HeuristicQueryEntityExtractor entityExtractor = new HeuristicQueryEntityExtractor();
        entityExtractor.load();

        pipeline = new QueryUnderstandingPipeline(
                new IdentitySpellFixer(),
                new NfkcQueryNormalizer(),
                new HeuristicIntentClassifier(loadKeywordMap(), new KeywordMatcher()),
                entityExtractor,
                new RoutingQueryExpander(properties, terminology, geminiExpander));
    }

    @Test
    void expandsGdpAbbreviationForRetrieval() {
        var analyzed = pipeline.analyze("GDP 2024", "en");

        assertThat(analyzed.intent()).isEqualTo(QueryIntentKind.LOOKUP);
        assertThat(analyzed.retrievalText()).contains("gross domestic product");
        assertThat(analyzed.retrievalText()).contains("2024");
        assertThat(analyzed.retrievalText()).contains("GDP");
    }

    @Test
    void classifiesGeorgianFactualQuestion() {
        var analyzed = pipeline.analyze("რა არის ინფლაცია", "ka");

        assertThat(analyzed.intent()).isEqualTo(QueryIntentKind.FACTUAL);
        assertThat(analyzed.normalized()).isEqualTo("რა არის ინფლაცია");
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
