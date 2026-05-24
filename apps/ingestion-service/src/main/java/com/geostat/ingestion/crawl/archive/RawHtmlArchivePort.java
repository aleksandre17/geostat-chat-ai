package com.geostat.ingestion.crawl.archive;

import java.util.Optional;

/** Optional raw HTML archive (S3/MinIO future — B-28). */
public interface RawHtmlArchivePort {

    Optional<String> store(String corpusName, String url, byte[] html);

    Optional<byte[]> load(String archiveKey);
}
