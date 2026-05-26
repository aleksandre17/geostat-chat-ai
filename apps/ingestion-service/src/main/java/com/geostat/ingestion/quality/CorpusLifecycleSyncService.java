package com.geostat.ingestion.quality;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.index.lifecycle.DocumentQdrantLifecycleSync;
import com.geostat.ingestion.index.lifecycle.DocumentServeState;
import com.geostat.ingestion.index.lifecycle.DocumentServeStateResolver;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Batch sync Qdrant payload metadata (serveState/pageKind/scoreBoost) without re-embedding. */
@Service
@Profile("db")
public class CorpusLifecycleSyncService {

    private final IngestionProperties properties;
    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;
    private final DocumentServeStateResolver serveStateResolver;
    private final DocumentQdrantLifecycleSync documentQdrantLifecycleSync;

    public CorpusLifecycleSyncService(
            IngestionProperties properties,
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository,
            DocumentServeStateResolver serveStateResolver,
            DocumentQdrantLifecycleSync documentQdrantLifecycleSync) {
        this.properties = properties;
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
        this.serveStateResolver = serveStateResolver;
        this.documentQdrantLifecycleSync = documentQdrantLifecycleSync;
    }

    @Transactional
    public CorpusLifecycleSyncReport syncQdrantMetadata(String corpusName) {
        if (!properties.indexing().enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED, "Indexing disabled — set geostat.ingestion.indexing.enabled=true");
        }
        CorpusEntity corpus = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corpus not found: " + corpusName));

        List<DocumentEntity> documents =
                documentRepository.findByCorpus_IdAndFetchStatus(corpus.getId(), DocumentFetchStatus.parsed);
        int dropped = 0;
        int updated = 0;
        for (DocumentEntity document : documents) {
            DocumentServeState state = serveStateResolver.resolve(document);
            documentQdrantLifecycleSync.syncDocumentEntity(document);
            if (state == DocumentServeState.DROPPED) {
                dropped++;
            } else {
                updated++;
            }
        }
        return new CorpusLifecycleSyncReport(corpusName, Instant.now(), documents.size(), updated, dropped);
    }

    public record CorpusLifecycleSyncReport(
            String corpusName, Instant syncedAt, int documentsProcessed, int metadataUpdated, int droppedRemoved) {}
}
