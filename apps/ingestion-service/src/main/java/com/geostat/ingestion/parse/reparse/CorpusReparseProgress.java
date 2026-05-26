package com.geostat.ingestion.parse.reparse;

import java.time.Instant;

public record CorpusReparseProgress(
        String corpusName,
        String status,
        int total,
        int processed,
        int accepted,
        int skipped,
        int failed,
        Instant startedAt,
        Instant finishedAt) {

    public static CorpusReparseProgress idle() {
        return new CorpusReparseProgress(null, "idle", 0, 0, 0, 0, 0, null, null);
    }

    public static CorpusReparseProgress running(
            String corpusName, int total, int processed, int accepted, int skipped, Instant startedAt) {
        return new CorpusReparseProgress(corpusName, "running", total, processed, accepted, skipped, 0, startedAt, null);
    }

    public static CorpusReparseProgress finished(
            String corpusName,
            int total,
            int processed,
            int accepted,
            int skipped,
            int failed,
            Instant startedAt,
            Instant finishedAt) {
        return new CorpusReparseProgress(
                corpusName, "finished", total, processed, accepted, skipped, failed, startedAt, finishedAt);
    }
}
