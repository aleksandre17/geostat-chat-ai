package com.geostat.ingestion.enrichment.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class TopicLabelPromptTemplate {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptRoot(String system, String user) {}

    private PromptRoot root;

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        root = mapper.readValue(
                new ClassPathResource("prompts/enrichment/topic-label.yaml").getInputStream(), PromptRoot.class);
    }

    public String system() {
        return root.system();
    }

    public String user(String samples) {
        return root.user().replace("{{samples}}", samples != null ? samples : "");
    }
}
