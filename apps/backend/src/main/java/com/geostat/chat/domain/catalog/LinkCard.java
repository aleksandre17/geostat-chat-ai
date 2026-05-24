package com.geostat.chat.domain.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User-facing citation card — catalog route or RAG source.
 * {@code sourceType}: catalog | rag | general | portal
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkCard(
        @JsonProperty("url") String url,
        @JsonProperty("titleKa") String titleKa,
        @JsonProperty("titleEn") String titleEn,
        @JsonProperty("type") String type,
        @JsonProperty("icon") String icon,
        @JsonProperty("emoji") String emoji,
        @JsonProperty("bgColor") String bgColor,
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("snippet") String snippet,
        @JsonProperty("relevanceScore") Double relevanceScore) {

    /** Catalog / curated link (legacy 7-arg call sites). */
    public LinkCard(String url, String titleKa, String titleEn, String type, String icon, String emoji, String bgColor) {
        this(
                url,
                titleKa,
                titleEn,
                type,
                icon,
                emoji,
                bgColor,
                defaultSourceType(type),
                null,
                null);
    }

    public static LinkCard fromCatalog(
            String url, String titleKa, String titleEn, String type, String icon, String bgColor) {
        return new LinkCard(url, titleKa, titleEn, type, icon, "", bgColor, "catalog", null, null);
    }

    public static LinkCard fromCatalog(
            String url,
            String titleKa,
            String titleEn,
            String type,
            String icon,
            String bgColor,
            String snippet) {
        return new LinkCard(url, titleKa, titleEn, type, icon, "", bgColor, "catalog", snippet, null);
    }

    public static LinkCard fromRag(
            String url, String titleKa, String titleEn, String snippet, double relevanceScore, String icon, String bgColor) {
        return new LinkCard(
                url, titleKa, titleEn, "source", icon, "", bgColor, "rag", snippet, relevanceScore);
    }

    /** @deprecated use {@link #fromRag(String, String, String, String, double, String, String)} */
    @Deprecated
    public static LinkCard fromRag(
            String url, String title, String snippet, double relevanceScore, String icon, String bgColor) {
        return fromRag(url, title, title, snippet, relevanceScore, icon, bgColor);
    }

    private static String defaultSourceType(String type) {
        if (type == null) {
            return "catalog";
        }
        return switch (type.toLowerCase()) {
            case "source" -> "rag";
            case "portal" -> "portal";
            case "general" -> "general";
            default -> "catalog";
        };
    }
}
