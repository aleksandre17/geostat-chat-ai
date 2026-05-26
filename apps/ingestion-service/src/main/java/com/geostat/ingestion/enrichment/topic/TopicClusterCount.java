package com.geostat.ingestion.enrichment.topic;

public final class TopicClusterCount {

    private TopicClusterCount() {}

    /** k = sqrt(N/10), bounded for small corpora (spec §5.U01g). */
    public static int forDocumentCount(int documentCount) {
        if (documentCount < 2) {
            return 0;
        }
        int k = (int) Math.round(Math.sqrt(documentCount / 10.0));
        k = Math.max(2, k);
        return Math.min(k, documentCount);
    }
}
