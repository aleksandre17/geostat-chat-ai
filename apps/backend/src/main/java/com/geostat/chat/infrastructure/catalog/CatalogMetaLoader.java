package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.catalog.LinkInfo;
import com.geostat.chat.domain.catalog.Topic;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Loads catalog constants from {@code catalog/catalog-meta.yaml} (B-24). */
@Component
public class CatalogMetaLoader {

    private record PortalEntry(String url, String urlEn, String titleKa, String titleEn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogMetaRoot(
            List<String> sectoralKeywords,
            LinkInfo sectoralAccounts,
            List<String> newsRelevantTopics,
            List<String> latestKeywords,
            List<PortalEntry> allPortals) {}

    private List<String> sectoralKeywords = List.of();
    private LinkInfo sectoralAccounts;
    private Set<Topic> newsRelevantTopics = Set.of();
    private List<String> latestKeywords = List.of();
    private List<LinkInfo> allPortals = List.of();

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        CatalogMetaRoot root = mapper.readValue(
                new ClassPathResource("catalog/catalog-meta.yaml").getInputStream(),
                CatalogMetaRoot.class);
        sectoralKeywords = root.sectoralKeywords() != null ? List.copyOf(root.sectoralKeywords()) : List.of();
        sectoralAccounts = root.sectoralAccounts();
        latestKeywords = root.latestKeywords() != null ? List.copyOf(root.latestKeywords()) : List.of();
        if (root.newsRelevantTopics() != null) {
            EnumSet<Topic> topics = EnumSet.noneOf(Topic.class);
            root.newsRelevantTopics().forEach(name -> topics.add(Topic.valueOf(name)));
            newsRelevantTopics = Set.copyOf(topics);
        }
        if (root.allPortals() != null) {
            allPortals = root.allPortals().stream()
                    .map(p -> p.urlEn() != null && !p.urlEn().isBlank()
                            ? new LinkInfo(p.url(), p.titleKa(), p.titleEn(), p.urlEn())
                            : new LinkInfo(p.url(), p.titleKa(), p.titleEn()))
                    .toList();
        }
    }

    public List<String> sectoralKeywords() {
        return sectoralKeywords;
    }

    public LinkInfo sectoralAccounts() {
        return sectoralAccounts;
    }

    public Set<Topic> newsRelevantTopics() {
        return newsRelevantTopics;
    }

    public List<String> latestKeywords() {
        return latestKeywords;
    }

    public List<LinkInfo> allPortals() {
        return allPortals;
    }
}
