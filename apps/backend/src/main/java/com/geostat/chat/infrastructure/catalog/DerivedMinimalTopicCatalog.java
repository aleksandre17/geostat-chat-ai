package com.geostat.chat.infrastructure.catalog;

import com.geostat.chat.domain.catalog.LinkInfo;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal {@link TopicCatalog} when {@code geostat.chat.catalog.source=derived}.
 * Link routing uses {@link com.geostat.chat.domain.catalog.DerivedCatalogReader}; this bean only
 * satisfies topic detection / result styling. Legacy topics.yaml was removed after G2 eval pass.
 */
@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class DerivedMinimalTopicCatalog implements TopicCatalog {

    private static final LinkInfo EMPTY_LINK = new LinkInfo("", "", "");

    private final PresentationStyleCatalog presentationStyles;

    public DerivedMinimalTopicCatalog(PresentationStyleCatalog presentationStyles) {
        this.presentationStyles = presentationStyles;
    }

    @Override
    public TopicDefinition get(Topic topic) {
        var style = presentationStyles.linkTypeStyle("general");
        TopicDefinition.TopicStyle topicStyle =
                new TopicDefinition.TopicStyle(style.icon(), style.bgColor(), style.lightBg());
        return TopicDefinition.of(topic, List.of(), List.of(), null, null, null, topicStyle, 0);
    }

    @Override
    public Collection<TopicDefinition> all() {
        return List.of(get(Topic.GENERAL));
    }

    @Override
    public List<LinkInfo> allPortals() {
        return List.of();
    }

    @Override
    public List<String> sectoralKeywords() {
        return List.of();
    }

    @Override
    public LinkInfo sectoralAccounts() {
        return EMPTY_LINK;
    }

    @Override
    public Set<Topic> newsRelevantTopics() {
        return EnumSet.noneOf(Topic.class);
    }

    @Override
    public List<String> latestKeywords() {
        return List.of();
    }

    @Override
    public List<LinkInfo> matchSpecificLinks(String query) {
        return List.of();
    }

    @Override
    public LinkInfo categoryNews(Topic topic, boolean isGeorgian) {
        return EMPTY_LINK;
    }
}
