package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads {@code catalog/small-talk.yaml} at startup. */
@Component
public class SmallTalkCatalogLoader {

    /** One localized string — ka + en. */
    public record LocalizedString(String ka, String en) {}

    /** One response group (greeting, farewell, …). maxLength=0 means unlimited. */
    public record SmallTalkGroup(
            String id,
            List<String> patterns,
            int maxLength,
            LocalizedString response) {}

    /** Config for portal-list detection. */
    public record PortalListConfig(
            List<String> portalKeywords,
            List<String> calculatorKeywords,
            List<String> listRequestKeywords,
            List<String> excludeKeywords,
            int maxGenericLength) {}

    /** Root YAML structure. */
    public record SmallTalkRoot(
            int version,
            List<SmallTalkGroup> groups,
            LocalizedString clarification,
            PortalListConfig portalList) {}

    private SmallTalkRoot root;

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/small-talk.yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        root = mapper.readValue(resource.getInputStream(), SmallTalkRoot.class);
    }

    public SmallTalkRoot get() {
        return root;
    }
}
