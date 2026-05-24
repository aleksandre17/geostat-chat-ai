package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicDefinition;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads topic definitions from {@code catalog/topics.yaml} (B-24). */
@Component
public class TopicCatalogLoader {

    private record TopicsRoot(Map<String, TopicDefinition> topics) {}

    private Map<Topic, TopicDefinition> registry = Map.of();

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        TopicsRoot root = mapper.readValue(
                new ClassPathResource("catalog/topics.yaml").getInputStream(),
                TopicsRoot.class);
        Map<Topic, TopicDefinition> map = new LinkedHashMap<>();
        if (root.topics() != null) {
            root.topics().forEach((name, def) -> map.put(Topic.valueOf(name), def));
        }
        registry = Map.copyOf(map);
    }

    public TopicDefinition get(Topic topic) {
        return registry.getOrDefault(topic, registry.get(Topic.GENERAL));
    }

    public Collection<TopicDefinition> all() {
        return registry.values();
    }
}
