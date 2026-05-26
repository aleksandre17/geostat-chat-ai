package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class QueryExpandPromptTemplate {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptRoot(String system, String user) {}

    private PromptRoot root;

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        root = mapper.readValue(
                new ClassPathResource("prompts/query/query-expand.yaml").getInputStream(), PromptRoot.class);
    }

    public String system() {
        return root.system();
    }

    public String user(String query, String locale) {
        return root.user()
                .replace("{{query}}", query != null ? query : "")
                .replace("{{locale}}", locale != null ? locale : "ka");
    }
}
