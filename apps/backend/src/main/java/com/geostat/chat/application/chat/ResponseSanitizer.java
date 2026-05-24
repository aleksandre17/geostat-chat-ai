package com.geostat.chat.application.chat;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Strips URLs and markdown link syntax that the AI accidentally includes in its response.
 */
@Component
public class ResponseSanitizer {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]\\(https?://[^)]+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET_LINK_PATTERN = Pattern.compile(
            "^[*•\\-]\\s*.*https?://.*$", Pattern.MULTILINE);

    public String strip(String response, boolean isGeorgian) {
        if (response == null || response.isBlank()) return response;
        String cleaned = BULLET_LINK_PATTERN.matcher(response).replaceAll("");
        cleaned = MARKDOWN_LINK_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned
                .replaceAll(":\\s*\n\\s*\n", ".\n")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("\\*\\s*\\n", "\n")
                .trim();
        if (cleaned.length() < 15) {
            cleaned = isGeorgian
                    ? "დეტალური ინფორმაცია იხილეთ ქვემოთ მოცემულ ბმულებზე."
                    : "Please see the links below for detailed information.";
        }
        return cleaned;
    }
}