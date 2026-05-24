package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.catalog.TopicStyleCatalog;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Assembles {@link ChatResult} from domain data (R-03…R-05). */
@Component
public class ChatResultFactory {

    private final TopicCatalog topicCatalog;

    public ChatResultFactory(TopicCatalog topicCatalog) {
        this.topicCatalog = topicCatalog;
    }

    public ChatResult build(
            String intro,
            List<LinkedExplanation> items,
            List<Topic> topics,
            boolean isGeorgian,
            String sessionId,
            String turnId,
            ChatResponseKind kind,
            List<RetrievedChunk> ragChunks) {
        List<LinkedExplanation> safeItems = items != null ? items : List.of();
        Topic primary = topics.isEmpty() ? Topic.GENERAL : topics.get(0);
        TopicDefinition.TopicStyle style = topicCatalog.get(primary).style();
        boolean grounded = ExplanationGroundingVerifier.isGrounded(safeItems, ragChunks, intro);
        int sourceCount = countSources(safeItems);
        return new ChatResult(
                intro,
                safeItems,
                isGeorgian ? "ka" : "en",
                primary.name(),
                topics.stream().map(Topic::name).collect(Collectors.toList()),
                style.icon(),
                style.bgColor(),
                sessionId,
                turnId,
                kind,
                grounded,
                sourceCount,
                null,
                null);
    }

    public ChatResult error(boolean isGeorgian, String sessionId) {
        String intro = isGeorgian
                ? "ტექნიკური ხარვეზი დაფიქსირდა. გთხოვთ, სცადოთ ხელახლა."
                : "A technical error occurred. Please try again.";
        TopicDefinition.TopicStyle style = topicCatalog.get(Topic.GENERAL).style();
        TopicStyleCatalog.LinkTypeStyle gs = TopicStyleCatalog.getLinkTypeStyle("general");
        LinkCard fallback = LinkCard.fromCatalog(
                "https://www.geostat.ge/ka/site-map",
                "საიტის რუკა",
                "Site Map",
                "general",
                gs != null ? gs.icon() : style.icon(),
                style.bgColor());
        return new ChatResult(
                intro,
                List.of(new LinkedExplanation(null, fallback)),
                isGeorgian ? "ka" : "en",
                Topic.GENERAL.name(),
                List.of(Topic.GENERAL.name()),
                style.icon(),
                style.bgColor(),
                sessionId,
                UUID.randomUUID().toString(),
                ChatResponseKind.error,
                false,
                1,
                "TECHNICAL_ERROR",
                intro);
    }

    private static int countSources(List<LinkedExplanation> items) {
        return (int) items.stream()
                .filter(item -> item.link() != null && item.link().url() != null && !item.link().url().isBlank())
                .count();
    }
}
