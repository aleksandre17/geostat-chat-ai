package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.chat.application.retrieval.ResponseRouter;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.chat.AiChatResult;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.prompt.UiStringKey;
import com.geostat.chat.domain.retrieval.RetrievalConfidenceAssessor;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.RetrievalConfidence;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiResponseParserTest {

    private AiResponseParser parser;
    private PromptCatalog promptCatalog;
    private RetrievalConfidenceAssessor confidenceAssessor;

    @BeforeEach
    void setUp() {
        TopicCatalog catalog = Mockito.mock(TopicCatalog.class);
        TopicDefinition def = TopicDefinition.of(
                Topic.STRUCTURE,
                List.of(),
                List.of(),
                null,
                null,
                null,
                new TopicDefinition.TopicStyle("icon", "#fff", "#eee"),
                0);
        Mockito.when(catalog.get(Topic.GENERAL)).thenReturn(def);
        promptCatalog = Mockito.mock(PromptCatalog.class);
        confidenceAssessor = Mockito.mock(RetrievalConfidenceAssessor.class);
        parser = new AiResponseParser(
                new ObjectMapper(), catalog, promptCatalog, confidenceAssessor, new ResponseRouter());
    }

    @Test
    void parseMainResponse_conceptEmptyItems_returnsIntroOnly() {
        LinkCard link = LinkCard.fromCatalog("https://www.geostat.ge/ka", "t", "t", "general", "i", "#fff");
        String raw = "{\"intro\":\"GDP არის მთლიანი შიდა პროდუქტი.\",\"items\":[]}";
        AiChatResult result = parser.parseMainResponse(raw, List.of(link), true);
        assertEquals("GDP არის მთლიანი შიდა პროდუქტი.", result.intro());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void parseClarification_rejectsUrlNotInWhitelist() {
        String raw = """
                {"intro":"question","items":[{"url":"https://evil.example/x","title":"x","explanation":"y"}]}
                """;
        AiChatResult result = parser.parseClarification(
                raw, true, Set.of("https://www.geostat.ge/ka"));
        assertEquals("question", result.intro());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void fallback_lowConfidence_returnsClarificationWithoutLinks() {
        Mockito.when(confidenceAssessor.assess(List.of())).thenReturn(RetrievalConfidence.LOW);
        Mockito.when(promptCatalog.uiString(UiStringKey.CLARIFICATION_FALLBACK, true))
                .thenReturn("clarify");
        LinkCard link = LinkCard.fromCatalog("https://www.geostat.ge/ka", "t", "t", "general", "i", "#fff");
        AiChatResult result = parser.fallback(true, List.of(link), List.of());
        assertEquals("clarify", result.intro());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void fallback_highConfidence_returnsCatalogLinks() {
        Mockito.when(confidenceAssessor.assess(Mockito.anyList())).thenReturn(RetrievalConfidence.HIGH);
        Mockito.when(promptCatalog.uiString(UiStringKey.LINKS_BELOW, false))
                .thenReturn("links");
        LinkCard link = LinkCard.fromCatalog("https://www.geostat.ge/en", "t", "t", "general", "i", "#fff");
        AiChatResult result = parser.fallback(false, List.of(link), List.of(new RetrievedChunk("doc", "https://x", "text", 0.9)));
        assertEquals("links", result.intro());
        assertEquals(1, result.items().size());
    }

    @Test
    void parseClarification_acceptsWhitelistedUrl() {
        String raw = """
                {"intro":"found","items":[{"url":"https://www.geostat.ge/ka/team","title":"t","explanation":"e"}]}
                """;
        AiChatResult result = parser.parseClarification(
                raw, true, Set.of("https://www.geostat.ge/ka/team"));
        assertEquals(1, result.items().size());
    }
}
