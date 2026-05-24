package com.geostat.chat.infrastructure.catalog;

import com.geostat.chat.domain.catalog.LinkInfo;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * YAML-backed topic catalog (B-24).
 * Data: {@code resources/catalog/{topics,catalog-meta,specific-links,news-categories}.yaml}
 */
@Component
public class YamlTopicCatalog implements TopicCatalog {

    private final TopicCatalogLoader topicLoader;
    private final CatalogMetaLoader metaLoader;
    private final SpecificLinkLoader specificLinkLoader;
    private final NewsCategoryLoader newsCategoryLoader;

    public YamlTopicCatalog(
            TopicCatalogLoader topicLoader,
            CatalogMetaLoader metaLoader,
            SpecificLinkLoader specificLinkLoader,
            NewsCategoryLoader newsCategoryLoader) {
        this.topicLoader = topicLoader;
        this.metaLoader = metaLoader;
        this.specificLinkLoader = specificLinkLoader;
        this.newsCategoryLoader = newsCategoryLoader;
    }

    @Override
    public TopicDefinition get(Topic topic) {
        return topicLoader.get(topic);
    }

    @Override
    public Collection<TopicDefinition> all() {
        return topicLoader.all();
    }

    @Override
    public List<LinkInfo> allPortals() {
        return metaLoader.allPortals();
    }

    @Override
    public List<String> sectoralKeywords() {
        return metaLoader.sectoralKeywords();
    }

    @Override
    public LinkInfo sectoralAccounts() {
        return metaLoader.sectoralAccounts();
    }

    @Override
    public Set<Topic> newsRelevantTopics() {
        return metaLoader.newsRelevantTopics();
    }

    @Override
    public List<String> latestKeywords() {
        return metaLoader.latestKeywords();
    }

    @Override
    public List<LinkInfo> matchSpecificLinks(String query) {
        return specificLinkLoader.findMatches(query);
    }

    @Override
    public LinkInfo categoryNews(Topic topic, boolean isGeorgian) {
        return newsCategoryLoader.getCategoryNews(topic, isGeorgian);
    }
}
