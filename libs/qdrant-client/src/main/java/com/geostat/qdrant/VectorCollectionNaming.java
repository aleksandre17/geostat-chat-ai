package com.geostat.qdrant;

import java.util.Locale;

/** Sanitize corpus names into Qdrant collection ids (shared ingestion + retrieval). */
public final class VectorCollectionNaming {

    private VectorCollectionNaming() {}

    public static String collectionForName(String corpusName) {
        if (corpusName == null || corpusName.isBlank()) {
            return "geostat-default";
        }
        String sanitized = corpusName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        return sanitized.isEmpty() ? "geostat-default" : sanitized;
    }
}
