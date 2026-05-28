package com.geostat.ingestion.enrichment.keyword;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.KeywordDeriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class KeywordEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final KeywordDeriver keywordDeriver;
    private final DocumentRepository documentRepository;
    private final EnrichmentProperties properties;
    private final GeorgianStopwordLoader stopwordLoader;

    public KeywordEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            KeywordDeriver keywordDeriver,
            DocumentRepository documentRepository,
            EnrichmentProperties properties,
            GeorgianStopwordLoader stopwordLoader) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.keywordDeriver = keywordDeriver;
        this.documentRepository = documentRepository;
        this.properties = properties;
        this.stopwordLoader = stopwordLoader;
    }

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
        Set<String> stops = stopwordLoader.stopwords();
        List<String> filtered = keywords.stream()
                .filter(kw -> kw != null)
                .filter(kw -> kw.length() >= 3)
                .filter(kw -> !stops.contains(kw.toLowerCase()))
                .filter(kw -> kw.chars().anyMatch(c -> !Character.isDigit(c)))
                .collect(Collectors.toList());
        String[] array = filtered.toArray(String[]::new);
        document.setKeywords(array);
        documentRepository.updateKeywords(document.getId(), array);
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
