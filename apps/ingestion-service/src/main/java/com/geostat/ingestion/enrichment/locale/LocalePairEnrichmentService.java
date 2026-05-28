package com.geostat.ingestion.enrichment.locale;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.LocalePairDeriver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class LocalePairEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final LocalePairDeriver localePairDeriver;
    private final DocumentLocalePairLinker localePairLinker;
    private final DocumentRepository documentRepository;
    private final EnrichmentProperties properties;

    public LocalePairEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            LocalePairDeriver localePairDeriver,
            DocumentLocalePairLinker localePairLinker,
            DocumentRepository documentRepository,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.localePairDeriver = localePairDeriver;
        this.localePairLinker = localePairLinker;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.localePairModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.locale_pair,
                modelVersion,
                properties.maxRetries(),
                this::shouldSkip,
                this::findPair,
                this::persistPair,
                "locale_pair");
    }

    private boolean shouldSkip(DocumentEntity document) {
        if (document.getLanguage() == null) {
            return true;
        }
        String language = document.getLanguage().toLowerCase();
        return !language.equals("ka") && !language.equals("en");
    }

    private Optional<UUID> findPair(DocumentEntity document) {
        return localePairDeriver.findPair(toContext(document));
    }

    private void persistPair(DocumentEntity document, Optional<UUID> pairId) {
        localePairLinker.link(
                document.getCorpus().getId(),
                document.getId(),
                document.getCanonicalUrl(),
                document.getLanguage());
        if (pairId.isEmpty()) {
            return;
        }
        DocumentEntity counterpart = documentRepository.findById(pairId.get()).orElse(null);
        if (counterpart == null) {
            return;
        }
        document.setLocalePairDocument(counterpart);
        counterpart.setLocalePairDocument(document);
        documentRepository.updateLocalePairDocId(document.getId(), counterpart.getId());
        documentRepository.updateLocalePairDocId(counterpart.getId(), document.getId());
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
