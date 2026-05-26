package com.geostat.ingestion.enrichment.pagekind;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.PageKindClassifier;
import com.geostat.platform.enrichment.PageKindResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class PageKindEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final PageKindClassifier pageKindClassifier;
    private final EnrichmentProperties properties;

    public PageKindEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            PageKindClassifier pageKindClassifier,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.pageKindClassifier = pageKindClassifier;
        this.properties = properties;
    }

    @Transactional
    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.pageKindModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.page_kind,
                modelVersion,
                properties.maxRetries(),
                this::shouldSkip,
                document -> pageKindClassifier.classify(toContext(document)),
                this::persistPageKind,
                "page_kind");
    }

    private boolean shouldSkip(DocumentEntity document) {
        return document.getCanonicalUrl() == null || document.getCanonicalUrl().isBlank();
    }

    private void persistPageKind(DocumentEntity document, PageKindResult result) {
        document.setPageKind(result.kind());
    }

    private static DocumentContext toContext(DocumentEntity document) {
        String sectionPath = Optional.ofNullable(document.getSectionPath()).orElseGet(List::of).stream()
                .collect(Collectors.joining(" > "));
        return new DocumentContext(
                document.getId(),
                document.getCanonicalUrl(),
                document.getTitle(),
                document.getContentText(),
                document.getLanguage(),
                sectionPath);
    }
}
