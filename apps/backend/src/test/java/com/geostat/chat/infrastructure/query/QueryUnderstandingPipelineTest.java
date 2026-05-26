package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geostat.chat.application.query.QueryUnderstandingPipeline;
import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.domain.query.QueryIntentKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryUnderstandingPipelineTest {

    private QueryUnderstandingPipeline pipeline;

    @BeforeEach
    void setUp() {
        QueryUnderstandingProperties properties = new QueryUnderstandingProperties();
        YamlTerminologyQueryExpander terminology = new YamlTerminologyQueryExpander();
        terminology.loadOverlay();
        GeminiQueryExpander geminiExpander = mock(GeminiQueryExpander.class);
        when(geminiExpander.expand(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.List.of());

        pipeline = new QueryUnderstandingPipeline(
                new IdentitySpellFixer(),
                new NfkcQueryNormalizer(),
                new HeuristicIntentClassifier(),
                new HeuristicQueryEntityExtractor(),
                new RoutingQueryExpander(properties, terminology, geminiExpander));
    }

    @Test
    void expandsGdpAbbreviationForRetrieval() {
        var analyzed = pipeline.analyze("GDP 2024", "en");

        assertThat(analyzed.intent()).isEqualTo(QueryIntentKind.LATEST);
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
}
