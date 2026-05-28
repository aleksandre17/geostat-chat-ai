package com.geostat.ingestion.enrichment.locale;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.locale.VectorSimilarity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.DocumentLocalePairEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.DocumentLocalePairRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.LocalePairDeriver;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class UrlPlusEmbeddingLocalePairer implements LocalePairDeriver {

    private final DocumentRepository documentRepository;
    private final DocumentLocalePairRepository pairRepository;
    private final EmbeddingPort embeddingPort;
    private final EnrichmentProperties properties;

    public UrlPlusEmbeddingLocalePairer(
            DocumentRepository documentRepository,
            DocumentLocalePairRepository pairRepository,
            EmbeddingPort embeddingPort,
            EnrichmentProperties properties) {
        this.documentRepository = documentRepository;
        this.pairRepository = pairRepository;
        this.embeddingPort = embeddingPort;
        this.properties = properties;
    }

    @Override
    public Optional<UUID> findPair(DocumentContext document) {
        DocumentEntity source = documentRepository.findById(document.documentId()).orElse(null);
        if (source == null || source.getCorpus() == null || document.language() == null) {
            return Optional.empty();
        }
        String language = document.language().toLowerCase();
        if (!language.equals("ka") && !language.equals("en")) {
            return Optional.empty();
        }

        UUID corpusId = source.getCorpus().getId();
        if (hasLocalePathSegment(document.url())) {
            DocumentLocalePairLinker.PathPair paths =
                    DocumentLocalePairLinker.toPathPair(document.url(), language);
            if (paths != null) {
                Optional<UUID> byUrl = findByCounterpartUrl(corpusId, language, paths);
                if (byUrl.isPresent()) {
                    return byUrl;
                }
                Optional<UUID> fromTable = findFromPairTable(corpusId, language, paths.pathKey());
                if (fromTable.isPresent()) {
                    return fromTable;
                }
            }
        }
        return findByTitleEmbedding(corpusId, document.documentId(), language, document.title());
    }

    static boolean hasLocalePathSegment(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String path = URI.create(url.strip()).getPath();
            return path != null && path.matches("^/(ka|en)(/.*)?$");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Optional<UUID> findByCounterpartUrl(
            UUID corpusId, String language, DocumentLocalePairLinker.PathPair paths) {
        String counterpartUrl = language.equals("ka") ? paths.enUrl() : paths.kaUrl();
        return documentRepository
                .findByCorpusIdAndUrlHash(corpusId, UrlHasher.hash(counterpartUrl))
                .map(DocumentEntity::getId);
    }

    private Optional<UUID> findFromPairTable(UUID corpusId, String language, String pathKey) {
        Optional<DocumentLocalePairEntity> pair =
                pairRepository.findByCorpus_IdAndPathKey(corpusId, pathKey);
        if (pair.isEmpty()) {
            return Optional.empty();
        }
        DocumentEntity counterpart =
                language.equals("ka") ? pair.get().getEnDocument() : pair.get().getKaDocument();
        return counterpart != null ? Optional.of(counterpart.getId()) : Optional.empty();
    }

    private Optional<UUID> findByTitleEmbedding(UUID corpusId, UUID documentId, String language, String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String oppositeLanguage = language.equals("ka") ? "en" : "ka";
        List<DocumentEntity> candidates = documentRepository.findByCorpus_IdAndLanguageAndFetchStatusAndIdNot(
                corpusId, oppositeLanguage, DocumentFetchStatus.parsed, documentId);

        float[] sourceVector = embeddingPort.embed(title);
        double threshold = properties.localePairSimilarityThreshold();
        UUID bestId = null;
        double bestScore = threshold;
        for (DocumentEntity candidate : candidates) {
            String candidateTitle = candidate.getTitle();
            if (candidateTitle == null || candidateTitle.isBlank()) {
                continue;
            }
            double score = VectorSimilarity.cosine(sourceVector, embeddingPort.embed(candidateTitle));
            if (score > bestScore) {
                bestScore = score;
                bestId = candidate.getId();
            }
        }
        return bestId != null ? Optional.of(bestId) : Optional.empty();
    }
}
