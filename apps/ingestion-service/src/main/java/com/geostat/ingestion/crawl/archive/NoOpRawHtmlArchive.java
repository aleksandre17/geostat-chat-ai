package com.geostat.ingestion.crawl.archive;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "geostat.ingestion.archive",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoOpRawHtmlArchive implements RawHtmlArchivePort {

    @Override
    public Optional<String> store(String corpusName, String url, byte[] html) {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> load(String archiveKey) {
        return Optional.empty();
    }
}
