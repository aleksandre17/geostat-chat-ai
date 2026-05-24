package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.catalog.LinkInfo;
import com.geostat.chat.domain.catalog.Topic;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@Component
public class NewsCategoryLoader {

    private record CategoryEntry(int id, String titleKa, String titleEn) {}

    private record NewsCategoriesRoot(
            String baseUrl,
            int defaultCategoryId,
            String defaultTitleKa,
            String defaultTitleEn,
            Map<String, CategoryEntry> categories) {}

    private String baseUrl = "https://www.geostat.ge";
    private CategoryEntry defaultEntry = new CategoryEntry(2, "სიახლეები", "News");
    private Map<Topic, CategoryEntry> byTopic = Map.of();

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        NewsCategoriesRoot root = mapper.readValue(
                new ClassPathResource("catalog/news-categories.yaml").getInputStream(),
                NewsCategoriesRoot.class);
        baseUrl = root.baseUrl() != null ? root.baseUrl() : baseUrl;
        defaultEntry = new CategoryEntry(
                root.defaultCategoryId(),
                root.defaultTitleKa(),
                root.defaultTitleEn());
        Map<Topic, CategoryEntry> map = new EnumMap<>(Topic.class);
        if (root.categories() != null) {
            for (var e : root.categories().entrySet()) {
                map.put(Topic.valueOf(e.getKey()), e.getValue());
            }
        }
        byTopic = Map.copyOf(map);
    }

    public LinkInfo getCategoryNews(Topic topic, boolean isGeorgian) {
        CategoryEntry entry = byTopic.getOrDefault(topic, defaultEntry);
        String lang = isGeorgian ? "ka" : "en";
        String url = "%s/%s/news?year=&month=&category=%d".formatted(baseUrl, lang, entry.id());
        return new LinkInfo(url, entry.titleKa(), entry.titleEn());
    }
}
