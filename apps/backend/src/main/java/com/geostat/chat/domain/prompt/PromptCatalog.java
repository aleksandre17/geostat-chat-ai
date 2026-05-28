package com.geostat.chat.domain.prompt;

/** Port for locale-aware Gemini system prompts (B-27). */
public interface PromptCatalog {

    String mainPrompt(boolean isGeorgian);

    String clarificationPrompt(boolean isGeorgian);

    String topicClassifierPrompt(String topicList);

    int promptVersion();

    String promptContentHash();

    /**
     * Returns a localised UI string by key from the {@code uiStrings} section
     * of {@code chat-prompts.yaml}. Returns the key itself when the entry is missing.
     */
    String uiString(UiStringKey key, boolean isGeorgian);
}
