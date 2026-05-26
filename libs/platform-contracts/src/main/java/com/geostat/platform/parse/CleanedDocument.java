package com.geostat.platform.parse;

import java.util.List;

/** Structured output of HTML content extraction. */
public record CleanedDocument(
        String title,
        String bodyText,
        String language,
        List<String> sectionPath,
        String metaDescription,
        String leadText,
        String displayDescription,
        int totalParagraphs,
        int boilerplateParagraphs) {

    public CleanedDocument {
        title = title == null ? "" : title;
        bodyText = bodyText == null ? "" : bodyText;
        language = language == null ? null : language;
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        metaDescription = metaDescription;
        leadText = leadText;
        displayDescription = displayDescription;
    }

    public int bodyLength() {
        return bodyText.length();
    }
}
