package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.SourceUrlNormalizer;
import com.geostat.chat.domain.catalog.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/** Parses Gemini structured JSON into intro + link items. */
@Component
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    private final ObjectMapper objectMapper;
    private final TopicCatalog topicCatalog;
    public AiResponseParser(ObjectMapper objectMapper, TopicCatalog topicCatalog) {
        this.objectMapper = objectMapper;
        this.topicCatalog = topicCatalog;
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
                String url = node.path("url").asText("").strip();
                String expl = node.path("explanation").asText("").strip();
                String key = SourceUrlNormalizer.normalize(url);
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
                return fallback(isGeorgian, links);
            }

            if (intro.isEmpty()) {
                intro = isGeorgian ? "იხილეთ შემდეგი რესურსები." : "See the following resources.";
            }
            return new AiChatResult(intro, items);
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON response, using fallback: {}", e.getMessage());
            return fallback(isGeorgian, links);
        }
    }

    public AiChatResult parseClarification(String raw, boolean isGeorgian, Set<String> allowedUrls) {
        try {
            JsonNode root = objectMapper.readTree(stripMarkdown(raw));
            String intro = root.path("intro").asText("").strip();

            List<LinkedExplanation> items = new ArrayList<>();
            TopicDefinition.TopicStyle style = topicCatalog.get(Topic.STRUCTURE).style();

            for (JsonNode node : root.path("items")) {
                String url = node.path("url").asText("").strip();
                if (url.isEmpty() || !CorpusUrlWhitelist.contains(allowedUrls, url)) {
                    continue;
                }
                String title = node.path("title").asText("").strip();
                String expl = node.path("explanation").asText("").strip();
                String label = title.isEmpty() ? expl : title;
                LinkCard card = LinkCard.fromCatalog(url, label, label, "general", style.icon(), style.bgColor());
                items.add(new LinkedExplanation(expl.isEmpty() ? null : expl, card));
            }

            if (intro.isEmpty()) {
                intro = isGeorgian ? "იხილეთ შემდეგი ინფორმაცია." : "See the following information.";
            }
            return new AiChatResult(intro, items);
        } catch (Exception e) {
            log.warn("Failed to parse clarification JSON: {}", e.getMessage());
            return AiChatResult.emptyIntro(null);
        }
    }

    public AiChatResult fallback(boolean isGeorgian, List<LinkCard> links) {
        String intro = isGeorgian
                ? "მოთხოვნილი ინფორმაცია იხილეთ ქვემოთ მოცემულ ბმულებზე."
                : "You'll find the requested information at the links below.";
        return AiChatResult.withLinks(intro, links);
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
