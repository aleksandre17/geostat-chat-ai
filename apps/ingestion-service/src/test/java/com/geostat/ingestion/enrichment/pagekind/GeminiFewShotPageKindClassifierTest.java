package com.geostat.ingestion.enrichment.pagekind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.enrichment.prompt.PageKindPromptTemplate;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.platform.enrichment.DocumentContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class GeminiFewShotPageKindClassifierTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private PageKindPromptTemplate promptTemplate;

    private GeminiFewShotPageKindClassifier classifier;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setPageKindModelVersion("gemini-2.0-flash-lite-pagekind@2026-05-25");
        classifier = new GeminiFewShotPageKindClassifier(chatClient, promptTemplate, properties);
    }

    @Test
    void classifyUsesHeuristicWithoutCallingGemini() {
        DocumentContext document = new DocumentContext(
                UUID.randomUUID(),
                "https://www.geostat.ge/ka/news/cpi-release",
                "News",
                "text",
                "ka",
                "");

        var result = classifier.classify(document);

        assertThat(result.kind()).isEqualTo(PageKindValues.NEWS);
        verify(chatClient, never()).prompt();
    }

    @Test
    void parsePageKindResponseNormalizesKind() {
        String raw = "{\"page_kind\":\"portal\",\"confidence\":0.91}";
        var result = GeminiFewShotPageKindClassifier.parsePageKindResponse(raw, "v1");
        assertThat(result.kind()).isEqualTo(PageKindValues.PORTAL);
        assertThat(result.confidence()).isEqualTo(0.91);
    }

    @Test
    void parsePageKindResponseMapsInvalidKindToUnknown() {
        String raw = "{\"page_kind\":\"blog\",\"confidence\":0.5}";
        var result = GeminiFewShotPageKindClassifier.parsePageKindResponse(raw, "v1");
        assertThat(result.kind()).isEqualTo(PageKindValues.UNKNOWN);
    }
}
