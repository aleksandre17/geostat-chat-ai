package com.geostat.ingestion.enrichment.keyword;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.KeywordDeriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class KeywordEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final KeywordDeriver keywordDeriver;
    private final EnrichmentProperties properties;

    public KeywordEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            KeywordDeriver keywordDeriver,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.keywordDeriver = keywordDeriver;
        this.properties = properties;
    }

    @Transactional
    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.keywordModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.keywords,
                modelVersion,
                properties.maxRetries(),
                this::shouldSkip,
                this::deriveKeywords,
                this::persistKeywords,
                "keywords");
    }

    private boolean shouldSkip(DocumentEntity document) {
        return document.getContentText() == null || document.getContentText().isBlank();
    }

    private List<String> deriveKeywords(DocumentEntity document) {
        int topN = properties.keywordTopN();
        List<String> merged = new ArrayList<>(keywordDeriver.deriveKeywords(toContext(document), topN));

        DocumentEntity localePair = document.getLocalePairDocument();
        if (localePair != null
                && localePair.getContentText() != null
                && !localePair.getContentText().isBlank()) {
            merged = mergeKeywords(merged, keywordDeriver.deriveKeywords(toContext(localePair), topN));
        }
        return merged;
    }

    private void persistKeywords(DocumentEntity document, List<String> keywords) {
        document.setKeywords(keywords.toArray(String[]::new));
    }

    static List<String> mergeKeywords(List<String> primary, List<String> secondary) {
        Map<String, String> deduped = new LinkedHashMap<>();
        for (String keyword : primary) {
            addKeyword(deduped, keyword);
        }
        for (String keyword : secondary) {
            addKeyword(deduped, keyword);
        }
        return new ArrayList<>(deduped.values());
    }

    private static void addKeyword(Map<String, String> deduped, String keyword) {
        if (keyword == null) {
            return;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        deduped.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
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
