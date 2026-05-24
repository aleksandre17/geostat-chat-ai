package com.geostat.chat.infrastructure.prompt;

import com.geostat.chat.domain.prompt.PromptCatalog;
import org.springframework.stereotype.Component;

/** YAML-backed prompt catalog (B-27). */
@Component
public class YamlPromptCatalog implements PromptCatalog {

    private final PromptCatalogLoader loader;

    public YamlPromptCatalog(PromptCatalogLoader loader) {
        this.loader = loader;
    }

    @Override
    public String mainPrompt(boolean isGeorgian) {
        return loader.main(isGeorgian);
    }

    @Override
    public String clarificationPrompt(boolean isGeorgian) {
        return loader.clarification(isGeorgian);
    }

    @Override
    public String topicClassifierPrompt(String topicList) {
        return loader.topicClassifier().replace("{TOPICS}", topicList);
    }

    @Override
    public int promptVersion() {
        return loader.version();
    }

    @Override
    public String promptContentHash() {
        return loader.contentHash();
    }
}
