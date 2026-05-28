package com.geostat.platform.contracts.retrieval;

/**
 * One retrieved passage for LLM context and citation cards (RAG-L11 display metadata).
 *
 * <p>{@code navBreadcrumb} — navigation path extracted from the source page
 * (e.g. "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"). Enables topic-area display.
 *
 * <p>{@code publishedAt} — ISO-8601 article publication date, extracted from JSON-LD / OpenGraph.
 * Enables date display in citation cards.
 */
public record RetrievedChunk(
        String documentId,
        String sourceUrl,
        String text,
        double score,
        String language,
        String pageTitle,
        String sectionPath,
        String pageDescription,
        String fetchedAt,
        String navBreadcrumb,
        String publishedAt) {

    public RetrievedChunk(String documentId, String sourceUrl, String text, double score) {
        this(documentId, sourceUrl, text, score, null, null, null, null, null, null, null);
    }

    public RetrievedChunk(
            String documentId,
            String sourceUrl,
            String text,
            double score,
            String language,
            String pageTitle,
            String sectionPath) {
        this(documentId, sourceUrl, text, score, language, pageTitle, sectionPath, null, null, null, null);
    }

    /** Full constructor without navBreadcrumb/publishedAt — for backward compatibility. */
    public RetrievedChunk(
            String documentId,
            String sourceUrl,
            String text,
            double score,
            String language,
            String pageTitle,
            String sectionPath,
            String pageDescription,
            String fetchedAt) {
        this(documentId, sourceUrl, text, score, language, pageTitle, sectionPath,
                pageDescription, fetchedAt, null, null);
    }
}
