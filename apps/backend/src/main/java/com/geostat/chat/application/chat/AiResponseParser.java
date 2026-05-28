package com.geostat.chat.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.chat.application.retrieval.ResponseRouter;
import com.geostat.chat.application.retrieval.SourceUrlNormalizer;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.retrieval.ResponseRoute;
import com.geostat.chat.domain.retrieval.RetrievalConfidenceAssessor;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.RetrievalConfidence;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.chat.AiChatResult;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.prompt.UiStringKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Parses Gemini structured JSON into intro + link items. */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    private final ObjectMapper objectMapper;
    private final TopicCatalog topicCatalog;
    private final PromptCatalog promptCatalog;
    private final RetrievalConfidenceAssessor confidenceAssessor;
    private final ResponseRouter responseRouter;

    public AiResponseParser(
            ObjectMapper objectMapper,
            TopicCatalog topicCatalog,
            PromptCatalog promptCatalog,
            RetrievalConfidenceAssessor confidenceAssessor,
            ResponseRouter responseRouter) {
        this.objectMapper = objectMapper;
        this.topicCatalog = topicCatalog;
        this.promptCatalog = promptCatalog;
        this.confidenceAssessor = confidenceAssessor;
        this.responseRouter = responseRouter;
    }

    public AiChatResult parseMainResponse(String raw, List<LinkCard> links, boolean isGeorgian) {
        try {
            JsonNode root = objectMapper.readTree(stripMarkdown(raw));
            String intro = root.path("intro").asText("").strip();

            Map<String, LinkCard> byUrl = new LinkedHashMap<>();
            for (LinkCard link : links) {
                byUrl.putIfAbsent(SourceUrlNormalizer.normalize(link.url()), link);
            }

            List<LinkedExplanation> items = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            JsonNode itemsNode = root.path("items");
            boolean aiRequestedItems = itemsNode.isArray() && itemsNode.size() > 0;

            for (JsonNode node : itemsNode) {
                String url  = node.path("url").asText("").strip();
                String expl = node.path("explanation").asText("").strip();
                String key  = SourceUrlNormalizer.normalize(url);
                LinkCard card = byUrl.get(key);
                if (card != null && seen.add(key)) {
                    items.add(new LinkedExplanation(expl.isEmpty() ? null : expl, card));
                }
            }

            if (!aiRequestedItems && !intro.isEmpty()) {
                return new AiChatResult(intro, List.of());
            }

            for (LinkCard card : links) {
                if (seen.add(SourceUrlNormalizer.normalize(card.url()))) {
                    items.add(new LinkedExplanation(null, card));
                }
            }

            if (items.isEmpty() && intro.isEmpty()) {
                return fallback(isGeorgian, links, List.of());
            }

            if (intro.isEmpty()) {
                intro = promptCatalog.uiString(UiStringKey.SEE_RESOURCES, isGeorgian);
            }
            return new AiChatResult(intro, items);
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON response, using fallback: {}", e.getMessage());
            return fallback(isGeorgian, links, List.of());
        }
    }

    public AiChatResult parseClarification(String raw, boolean isGeorgian, Set<String> allowedUrls) {
        try {
            JsonNode root = objectMapper.readTree(stripMarkdown(raw));
            String intro = root.path("intro").asText("").strip();

            List<LinkedExplanation> items = new ArrayList<>();
            // Use GENERAL style for clarification — STRUCTURE is a layout hint, not a semantic style
            TopicDefinition.TopicStyle style = topicCatalog.get(Topic.GENERAL).style();

            for (JsonNode node : root.path("items")) {
                String url = node.path("url").asText("").strip();
                if (url.isEmpty() || !CorpusUrlWhitelist.contains(allowedUrls, url)) {
                    continue;
                }
                String title = node.path("title").asText("").strip();
                String expl  = node.path("explanation").asText("").strip();
                String label = title.isEmpty() ? expl : title;
                LinkCard card = LinkCard.fromCatalog(url, label, label, "general", style.icon(), style.bgColor());
                items.add(new LinkedExplanation(expl.isEmpty() ? null : expl, card));
            }

            if (intro.isEmpty()) {
                intro = promptCatalog.uiString(UiStringKey.SEE_INFORMATION, isGeorgian);
            }
            return new AiChatResult(intro, items);
        } catch (Exception e) {
            log.warn("Failed to parse clarification JSON: {}", e.getMessage());
            return AiChatResult.emptyIntro(null);
        }
    }

    public AiChatResult fallback(boolean isGeorgian, List<LinkCard> links) {
        return fallback(isGeorgian, links, List.of());
    }

    public AiChatResult fallback(boolean isGeorgian, List<LinkCard> links, List<RetrievedChunk> ragChunks) {
        RetrievalConfidence confidence = resolveFallbackConfidence(ragChunks, links);
        ResponseRoute route = responseRouter.route(confidence);
        return switch (route) {
            case CLARIFY, REFUSE_SUGGEST_TOPICS -> AiChatResult.emptyIntro(
                    promptCatalog.uiString(UiStringKey.CLARIFICATION_FALLBACK, isGeorgian));
            case ANSWER_WITH_CITATIONS, ANSWER_WITH_SUGGESTIONS -> AiChatResult.withLinks(
                    promptCatalog.uiString(UiStringKey.LINKS_BELOW, isGeorgian), links);
        };
    }

    private RetrievalConfidence resolveFallbackConfidence(List<RetrievedChunk> ragChunks, List<LinkCard> links) {
        List<RetrievedChunk> chunks = ragChunks != null ? ragChunks : List.of();
        RetrievalConfidence assessed = confidenceAssessor.assess(chunks);
        if (assessed == RetrievalConfidence.NONE && links != null && !links.isEmpty()) {
            return RetrievalConfidence.MEDIUM;
        }
        return assessed;
    }

    static String stripMarkdown(String raw) {
        if (raw == null) return "{}";
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").strip();
        }
        return s;
    }
}
