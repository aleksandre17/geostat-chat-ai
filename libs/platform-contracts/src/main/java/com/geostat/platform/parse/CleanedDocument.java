package com.geostat.platform.parse;

import java.time.Instant;
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
        int boilerplateParagraphs,
        String canonicalUrl,
        String pageKind,
        String navBreadcrumb,
        Instant publishedAt) {

    public CleanedDocument {
        title = title == null ? "" : title;
        bodyText = bodyText == null ? "" : bodyText;
        language = language == null ? null : language;
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        metaDescription = metaDescription;
        leadText = leadText;
        displayDescription = displayDescription;
        canonicalUrl = canonicalUrl == null ? "" : canonicalUrl;
        pageKind = pageKind;
    }

    /** Alias for validators referencing {@code body()}. */
    public String body() {
        return bodyText;
    }

    public int bodyLength() {
        return bodyText.length();
    }

    /** Single-line body for callers that need whitespace-collapsed text. */
    public String bodyFlat() {
        return bodyText.replaceAll("\\s+", " ").strip();
    }

    public CleanedDocument withBodyText(String newBody) {
        return new CleanedDocument(
                title,
                newBody,
                language,
                sectionPath,
                metaDescription,
                leadText,
                displayDescription,
                totalParagraphs,
                boilerplateParagraphs,
                canonicalUrl,
                pageKind,
                navBreadcrumb,
                publishedAt);
    }

    public CleanedDocument withLanguage(String newLanguage) {
        return new CleanedDocument(
                title,
                bodyText,
                newLanguage,
                sectionPath,
                metaDescription,
                leadText,
                displayDescription,
                totalParagraphs,
                boilerplateParagraphs,
                canonicalUrl,
                pageKind,
                navBreadcrumb,
                publishedAt);
    }

    public CleanedDocument withPublishedAt(Instant newPublishedAt) {
        return new CleanedDocument(
                title,
                bodyText,
                language,
                sectionPath,
                metaDescription,
                leadText,
                displayDescription,
                totalParagraphs,
                boilerplateParagraphs,
                canonicalUrl,
                pageKind,
                navBreadcrumb,
                newPublishedAt);
    }

    /** Minimal marker for pages with a robots noindex directive (no body extraction). */
    public static CleanedDocument noindexMarker(String url) {
        return new CleanedDocument(
                "",
                "",
                null,
                List.of(),
                null,
                null,
                null,
                0,
                0,
                url,
                "noindex",
                null,
                null);
    }

    public boolean isNoindex() {
        return "noindex".equals(pageKind());
    }
}
