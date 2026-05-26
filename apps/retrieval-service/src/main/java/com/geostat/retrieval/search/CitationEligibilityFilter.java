package com.geostat.retrieval.search;

/** Spec §17 — only live/partial_enriched, non-navigation chunks are citable. */
public final class CitationEligibilityFilter {

    private CitationEligibilityFilter() {}

    public static boolean isCitable(String serveState, String pageKind) {
        if ("navigation".equalsIgnoreCase(pageKind)) {
            return false;
        }
        if (serveState == null || serveState.isBlank()) {
            return true;
        }
        return "live".equalsIgnoreCase(serveState) || "partial_enriched".equalsIgnoreCase(serveState);
    }
}
