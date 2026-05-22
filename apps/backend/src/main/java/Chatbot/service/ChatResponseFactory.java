package Chatbot.service;

import Chatbot.catalog.TopicRegistry;
import Chatbot.catalog.TopicStyleCatalog;
import Chatbot.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles ChatResponse objects from domain data.
 * Single responsibility: response construction — not orchestration.
 */
@Component
public class ChatResponseFactory {

    public ChatResponse build(String intro, List<LinkedExplanation> items, List<Topic> topics,
                               boolean isGeorgian, String sessionId) {
        Topic primary = topics.isEmpty() ? Topic.GENERAL : topics.get(0);
        TopicDefinition.TopicStyle style = TopicRegistry.get(primary).style();
        return new ChatResponse(
                intro,
                items,
                isGeorgian ? "ka" : "en",
                primary.name(),
                topics.stream().map(Topic::name).collect(Collectors.toList()),
                style.icon(),
                "",
                style.bgColor(),
                sessionId
        );
    }

    public ChatResponse error(boolean isGeorgian, String sessionId) {
        String intro = isGeorgian
                ? "ტექნიკური ხარვეზი დაფიქსირდა. გთხოვთ, სცადოთ ხელახლა."
                : "A technical error occurred. Please try again.";
        TopicDefinition.TopicStyle style = TopicRegistry.get(Topic.GENERAL).style();
        TopicStyleCatalog.LinkTypeStyle gs = TopicStyleCatalog.getLinkTypeStyle("general");
        LinkCard fallback = new LinkCard(
                "https://www.geostat.ge/ka/site-map",
                "საიტის რუკა", "Site Map",
                "general", gs != null ? gs.icon() : style.icon(), "", style.bgColor());
        return build(intro, List.of(new LinkedExplanation(null, fallback)),
                List.of(Topic.GENERAL), isGeorgian, sessionId);
    }
}