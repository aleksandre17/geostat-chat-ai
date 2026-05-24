package com.geostat.chat.domain.prompt;

/** Port for locale-aware Gemini system prompts (B-27). */
public interface PromptCatalog {

    String mainPrompt(boolean isGeorgian);

    String clarificationPrompt(boolean isGeorgian);

    String topicClassifierPrompt(String topicList);

    int promptVersion();

    String promptContentHash();
}
