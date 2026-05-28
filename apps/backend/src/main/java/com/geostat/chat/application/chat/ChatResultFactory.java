package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.prompt.UiStringKey;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Assembles {@link ChatResult} from domain data (R-03…R-05). */
@Component
public class ChatResultFactory {

    private final TopicCatalog topicCatalog;
    private final PresentationStyleCatalog presentationStyles;
    private final PromptCatalog promptCatalog;
    private final ExplanationGroundingVerifier groundingVerifier;
    private final String errorFallbackUrl;

    public ChatResultFactory(
            TopicCatalog topicCatalog,
            PresentationStyleCatalog presentationStyles,
            PromptCatalog promptCatalog,
            ExplanationGroundingVerifier groundingVerifier,
            @Value("${geostat.chat.error-fallback-url}") String errorFallbackUrl) {
        this.topicCatalog       = topicCatalog;
        this.presentationStyles = presentationStyles;
        this.promptCatalog      = promptCatalog;
        this.groundingVerifier  = groundingVerifier;
        this.errorFallbackUrl   = errorFallbackUrl;
    }

    public ChatResult build(
            String intro,
            List<LinkedExplanation> items,
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            boolean isGeorgian,
            String sessionId,
            String turnId,
            ChatResponseKind kind,
            List<RetrievedChunk> ragChunks) {
        List<LinkedExplanation> safeItems = items != null ? items : List.of();
        Topic primary = topics.isEmpty() ? Topic.GENERAL : topics.get(0);
        TopicDefinition.TopicStyle style = topicCatalog.get(primary).style();
        boolean grounded = groundingVerifier.isGrounded(safeItems, ragChunks, intro);
        int sourceCount = countSources(safeItems);
        CatalogTopicLabelResolver.Labels labels =
                topicLabels != null ? topicLabels : fallbackLabels(topics);
        return new ChatResult(
                intro,
                safeItems,
                isGeorgian ? "ka" : "en",
                labels.primary(),
                labels.all(),
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
        String intro      = promptCatalog.uiString(UiStringKey.ERROR_INTRO, isGeorgian);
        String mapTitle   = promptCatalog.uiString(UiStringKey.ERROR_SITE_MAP_TITLE, isGeorgian);
        TopicDefinition.TopicStyle style = topicCatalog.get(Topic.GENERAL).style();
        var gs = presentationStyles.linkTypeStyle("general");
        LinkCard fallback = LinkCard.fromCatalog(
                errorFallbackUrl,
                mapTitle,
                mapTitle,
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
                ChatResponseKind.error.name(),
                intro);
    }

    private static CatalogTopicLabelResolver.Labels fallbackLabels(List<Topic> topics) {
        List<Topic> safe = topics == null || topics.isEmpty() ? List.of(Topic.GENERAL) : topics;
        List<String> names = safe.stream().map(Topic::name).toList();
        return new CatalogTopicLabelResolver.Labels(names.get(0), names);
    }

    private static int countSources(List<LinkedExplanation> items) {
        return (int) items.stream()
                .filter(item -> item.link() != null
                        && item.link().url() != null
                        && !item.link().url().isBlank())
                .count();
    }
}
