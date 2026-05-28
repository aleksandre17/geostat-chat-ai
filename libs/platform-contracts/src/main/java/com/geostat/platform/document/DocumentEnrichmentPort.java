package com.geostat.platform.document;

import java.util.UUID;

/**
 * Write port for the enrichment lifecycle phase.
 * Only enrichment services should inject this port.
 */
public interface DocumentEnrichmentPort {

    /** Update summary columns: summary_ka, summary_en. */
    void updateSummary(UUID documentId, String summaryKa, String summaryEn);

    /** Update keywords column (text[]). */
    void updateKeywords(UUID documentId, String[] keywords);

    /** Update page kind classification column. */
    void updatePageKind(UUID documentId, String pageKind);
}
