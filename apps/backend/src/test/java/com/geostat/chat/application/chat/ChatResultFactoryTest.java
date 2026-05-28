package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.infrastructure.catalog.YamlPresentationStyleCatalog;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatResultFactoryTest {

    @Test
    void grounded_whenRagItemOrCitedExplanation() {
        TopicCatalog catalog = mockCatalog();
        ChatResultFactory factory = new ChatResultFactory(
                catalog,
                YamlPresentationStyleCatalog.fromClasspath(),
                Mockito.mock(PromptCatalog.class),
                new ExplanationGroundingVerifier(20, 180),
                "https://www.geostat.ge/ka");

        ChatResult cited = factory.build(
                "passage about consumer price index measurement monthly",
                List.of(),
                List.of(Topic.PRICES),
                new CatalogTopicLabelResolver.Labels("PRICES", List.of("PRICES")),
                false,
                "s",
                "t",
                ChatResponseKind.answer,
                List.of(new RetrievedChunk(
                        "d",
                        "https://www.geostat.ge/ka/cpi",
                        "consumer price index measurement monthly data",
                        0.9)));
        assertTrue(cited.grounded());
    }

    private static TopicCatalog mockCatalog() {
        TopicCatalog catalog = Mockito.mock(TopicCatalog.class);
        TopicDefinition def = TopicDefinition.of(
                Topic.GENERAL,
                List.of(),
                List.of(),
                null,
                null,
                null,
                new TopicDefinition.TopicStyle("i", "#fff", "#eee"),
                0);
        Mockito.when(catalog.get(Topic.GENERAL)).thenReturn(def);
        Mockito.when(catalog.get(Topic.PRICES)).thenReturn(def);
        return catalog;
    }
}
