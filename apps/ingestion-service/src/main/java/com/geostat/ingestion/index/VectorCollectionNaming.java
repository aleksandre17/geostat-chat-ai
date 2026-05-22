package com.geostat.ingestion.index;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import java.util.Locale;

public final class VectorCollectionNaming {

    private VectorCollectionNaming() {}

    public static String collectionFor(CorpusEntity corpus) {
        String raw = corpus.getName().trim().toLowerCase(Locale.ROOT);
        String sanitized = raw.replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        if (sanitized.isEmpty()) {
            return "geostat-default";
        }
        return sanitized;
    }
}
