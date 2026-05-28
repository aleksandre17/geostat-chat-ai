package com.geostat.chat.application.chat;

import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.prompt.UiStringKey;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Strips URLs and markdown link syntax that the AI accidentally includes in its response.
 * Applies a configurable minimum-length guard and replaces short/empty results with a
 * locale-aware fallback sourced from the prompt catalog.
 */
@Component
public class ResponseSanitizer {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]\\(https?://[^)]+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET_LINK_PATTERN = Pattern.compile(
            "^[*•\\-]\\s*.*https?://.*$", Pattern.MULTILINE);

    private final int minIntroChars;
    private final PromptCatalog promptCatalog;

    public ResponseSanitizer(
            @Value("${geostat.chat.sanitizer.min-intro-chars:15}") int minIntroChars,
            PromptCatalog promptCatalog) {
        this.minIntroChars  = minIntroChars;
        this.promptCatalog  = promptCatalog;
    }

    public String strip(String response, boolean isGeorgian) {
        if (response == null || response.isBlank()) {
            return promptCatalog.uiString(UiStringKey.SANITIZER_FALLBACK, isGeorgian);
        }
        String cleaned = BULLET_LINK_PATTERN.matcher(response).replaceAll("");
        cleaned = MARKDOWN_LINK_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned
                .replaceAll(":\\s*\n\\s*\n", ".\n")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("\\*\\s*\\n", "\n")
                .trim();
        if (cleaned.length() < minIntroChars) {
            return promptCatalog.uiString(UiStringKey.SANITIZER_FALLBACK, isGeorgian);
        }
        return cleaned;
    }
}
