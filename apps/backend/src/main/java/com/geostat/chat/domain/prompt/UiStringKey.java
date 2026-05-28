package com.geostat.chat.domain.prompt;

/**
 * Type-safe keys for {@link PromptCatalog#uiString(UiStringKey, boolean)}.
 * Each constant maps to a camelCase key in the {@code uiStrings} section of
 * {@code prompts/chat-prompts.yaml}.
 */
public enum UiStringKey {
    ERROR_INTRO,
    ERROR_SITE_MAP_TITLE,
    PORTAL_LIST_INTRO,
    SEE_RESOURCES,
    SEE_INFORMATION,
    LINKS_BELOW,
    SANITIZER_FALLBACK,
    CLARIFICATION_FALLBACK;

    /** Maps enum name to camelCase YAML key. ERROR_INTRO → "errorIntro". */
    public String yamlKey() {
        String[] parts = name().split("_");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
