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
public class SummaryPromptTemplate {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptRoot(String system, String user) {}

    private PromptRoot root;

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        root = mapper.readValue(
                new ClassPathResource("prompts/enrichment/summary.yaml").getInputStream(), PromptRoot.class);
    }

    public String system() {
        return root.system();
    }

    public String user(String title, String sectionPath, String contentText) {
        return root.user()
                .replace("{{title}}", nullToEmpty(title))
                .replace("{{sectionPath}}", nullToEmpty(sectionPath))
                .replace("{{contentText}}", truncate(contentText, 8000));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value != null ? value : "";
        }
        return value.substring(0, maxLen);
    }
}
