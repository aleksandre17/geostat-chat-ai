package com.geostat.chat.domain.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Port: topic definitions, detection rules, and catalog constants. */
public interface TopicCatalog {

    TopicDefinition get(Topic topic);

    Collection<TopicDefinition> all();

    List<LinkInfo> allPortals();

    List<String> sectoralKeywords();

    LinkInfo sectoralAccounts();

    Set<Topic> newsRelevantTopics();

    List<String> latestKeywords();

    /** Keyword-triggered links (highest priority in ResponseBuilder). */
    List<LinkInfo> matchSpecificLinks(String query);

    /** Category-filtered news page for a topic. */
    LinkInfo categoryNews(Topic topic, boolean isGeorgian);
}
