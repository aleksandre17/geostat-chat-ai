package com.geostat.platform.contracts.retrieval;

/**
 * RAG search request (skeleton — fields will grow with indexing model).
 */
public record RetrievalQuery(
        String text,
        String locale,
        int maxChunks,
        String corpusName
) {
    public RetrievalQuery {
        if (maxChunks <= 0) {
            maxChunks = 8;
        }
    }

    public RetrievalQuery(String text, String locale, int maxChunks) {
        this(text, locale, maxChunks, null);
    }
}
