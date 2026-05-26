package com.geostat.ingestion.enrichment.vectors;

import com.geostat.ingestion.persistence.entity.DocumentEntity;

public final class DocumentSummaryText {

    private DocumentSummaryText() {}

    public static String pickForEmbedding(DocumentEntity document) {
        if (document == null) {
            return "";
        }
        String language = document.getLanguage();
        if (language != null && language.equalsIgnoreCase("en")) {
            return firstNonBlank(document.getSummaryEn(), document.getSummaryKa());
        }
        return firstNonBlank(document.getSummaryKa(), document.getSummaryEn());
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "";
    }
}
