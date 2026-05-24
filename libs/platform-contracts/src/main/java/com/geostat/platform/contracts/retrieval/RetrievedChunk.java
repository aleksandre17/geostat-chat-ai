package com.geostat.platform.contracts.retrieval;

/** One retrieved passage for LLM context and citation cards (RAG-L11 display metadata). */
public record RetrievedChunk(
        String documentId,
        String sourceUrl,
        String text,
        double score,
        String language,
        String pageTitle,
        String sectionPath,
        String pageDescription,
        String fetchedAt) {

    public RetrievedChunk(String documentId, String sourceUrl, String text, double score) {
        this(documentId, sourceUrl, text, score, null, null, null, null, null);
    }

    public RetrievedChunk(
            String documentId,
            String sourceUrl,
            String text,
            double score,
            String language,
            String pageTitle,
            String sectionPath) {
        this(documentId, sourceUrl, text, score, language, pageTitle, sectionPath, null, null);
    }
}
