package com.geostat.platform.document;

import java.util.UUID;

/**
 * Write port for the crawl lifecycle phase.
 * Only the crawl layer should inject this port.
 *
 * <p>Prevents enrichment, topic mining, and authority derivation from
 * accidentally overwriting crawl-managed columns via {@code DocumentRepository.save()}.
 */
public interface DocumentCrawlPort {

    /**
     * Update fetch result columns: fetch_status, canonical_url, content_hash,
     * http_status, error_message, fetched_at, content_length.
     */
    void updateFetchResult(UUID documentId, String fetchStatus, String canonicalUrl,
                           String contentHash, Integer httpStatus, String errorMessage);

    /**
     * Update content quality columns: content_text, word_count.
     */
    void updateContent(UUID documentId, String contentText, int wordCount);
}
