package com.geostat.ingestion.quality;

import java.time.Instant;

public record CorpusReindexReport(
        String corpusName,
        Instant startedAt,
        int documentsQueued,
        String mode) {}
