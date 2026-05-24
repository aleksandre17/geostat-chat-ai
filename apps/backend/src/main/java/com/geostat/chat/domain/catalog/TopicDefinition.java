package com.geostat.chat.domain.catalog;

import java.util.List;

/**
 * Single source of truth for one topic.
 * Adding a new topic = one new TopicDefinition in TopicRegistry.
 */
public record TopicDefinition(
        Topic            topic,
        List<TopicRule>  rules,
        List<PortalInfo> portals,
        LinkInfo         statistics,
        LinkInfo         metadata,
        LinkInfo         methodology,
        List<LinkInfo>   specialLinks,   // used for MANAGEMENT, ABOUT_US, SURVEYS, etc.
        TopicStyle       style,
        int              newsCategoryId  // 0 = no category news
) {
    public record TopicStyle(String icon, String bgColor, String lightBg) {}

    // Convenience constructor without specialLinks / newsCategoryId
    public static TopicDefinition of(
            Topic topic, List<TopicRule> rules,
            List<PortalInfo> portals, LinkInfo statistics,
            LinkInfo metadata, LinkInfo methodology,
            TopicStyle style, int newsCategoryId) {
        return new TopicDefinition(topic, rules, portals, statistics,
                metadata, methodology, List.of(), style, newsCategoryId);
    }
}