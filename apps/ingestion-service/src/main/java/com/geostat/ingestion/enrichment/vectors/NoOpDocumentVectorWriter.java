package com.geostat.ingestion.enrichment.vectors;

import com.geostat.platform.enrichment.DocumentVectorWriter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(
        prefix = "geostat.ingestion.enrichment",
        name = "named-vectors-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoOpDocumentVectorWriter implements DocumentVectorWriter {

    private static final Logger log = LoggerFactory.getLogger(NoOpDocumentVectorWriter.class);

    @Override
    public void writeNamedVector(UUID documentId, String vectorName, float[] embedding) {
        log.debug(
                "named vector write skipped (named-vectors-enabled=false) document={} vector={}",
                documentId,
                vectorName);
    }
}
