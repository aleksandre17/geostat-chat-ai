package com.geostat.ingestion.locale;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.DocumentLocalePairEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentLocalePairRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Links /ka/... and /en/... document pairs by normalized path key. */
@Component
@Profile("db")
public class DocumentLocalePairLinker {

    private final DocumentLocalePairRepository pairRepository;
    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;

    public DocumentLocalePairLinker(
            DocumentLocalePairRepository pairRepository,
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository) {
        this.pairRepository = pairRepository;
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public void link(UUID corpusId, UUID documentId, String canonicalUrl, String language) {
        if (corpusId == null || documentId == null || language == null) {
            return;
        }
        String lang = language.toLowerCase();
        if (!lang.equals("ka") && !lang.equals("en")) {
            return;
        }
        PathPair paths = toPathPair(canonicalUrl, lang);
        if (paths == null) {
            return;
        }
        CorpusEntity corpus = corpusRepository.getReferenceById(corpusId);
        DocumentEntity document = documentRepository.getReferenceById(documentId);
        DocumentLocalePairEntity pair = pairRepository
                .findByCorpus_IdAndPathKey(corpusId, paths.pathKey())
                .orElseGet(DocumentLocalePairEntity::new);
        pair.setCorpus(corpus);
        pair.setPathKey(paths.pathKey());
        pair.setKaUrl(paths.kaUrl());
        pair.setEnUrl(paths.enUrl());
        if ("ka".equals(lang)) {
            pair.setKaDocument(document);
        } else {
            pair.setEnDocument(document);
        }
        pairRepository.save(pair);
    }

    public static PathPair toPathPair(String url, String lang) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.strip());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String normalized = path.replaceFirst("^/(ka|en)", "");
            if (normalized.isEmpty()) {
                normalized = "/";
            }
            String pathKey = normalized.toLowerCase();
            String base = uri.getScheme() + "://" + uri.getHost();
            String kaUrl = base + "/ka" + ("/".equals(normalized) ? "" : normalized);
            String enUrl = base + "/en" + ("/".equals(normalized) ? "" : normalized);
            return new PathPair(pathKey, kaUrl, enUrl);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record PathPair(String pathKey, String kaUrl, String enUrl) {}
}
