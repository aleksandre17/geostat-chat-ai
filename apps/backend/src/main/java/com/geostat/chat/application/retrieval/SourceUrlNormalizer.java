package com.geostat.chat.application.retrieval;

/**
 * Normalizes URLs for catalog/RAG deduplication (fragment stripped, trailing slash trimmed).
 */
public final class SourceUrlNormalizer {

    private SourceUrlNormalizer() {}

    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = url.strip();
        int hash = normalized.indexOf('#');
        if (hash >= 0) {
            normalized = normalized.substring(0, hash);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase();
    }
}
