package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.Topic;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "yaml", matchIfMissing = true)
public class YamlCatalogTopicLabelResolver implements CatalogTopicLabelResolver {

    @Override
    public Labels resolve(List<Topic> detectedTopics, String query, String language, boolean georgian) {
        List<Topic> topics = detectedTopics == null || detectedTopics.isEmpty() ? List.of(Topic.GENERAL) : detectedTopics;
        List<String> names = topics.stream().map(Topic::name).toList();
        return new Labels(names.get(0), names);
    }
}
