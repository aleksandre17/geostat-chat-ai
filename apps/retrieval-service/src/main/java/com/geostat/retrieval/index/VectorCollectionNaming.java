package com.geostat.retrieval.index;

import java.util.Locale;

public final class VectorCollectionNaming {

    private VectorCollectionNaming() {}

    public static String collectionForCorpusName(String corpusName) {
        if (corpusName == null || corpusName.isBlank()) {
            return "geostat-default";
        }
        String sanitized =
                corpusName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        return sanitized.isEmpty() ? "geostat-default" : sanitized;
    }
}
