package com.geostat.ingestion.index.lifecycle;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.index.qdrant.QdrantVectorStore;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.qdrant.VectorCollectionNaming;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class DocumentQdrantLifecycleSync {

    private static final Logger log = LoggerFactory.getLogger(DocumentQdrantLifecycleSync.class);

    private final IngestionProperties properties;
    private final DocumentRepository documentRepository;
    private final DocumentServeStateResolver serveStateResolver;
    private final QdrantVectorStore vectorStore;

    public DocumentQdrantLifecycleSync(
            IngestionProperties properties,
            DocumentRepository documentRepository,
            DocumentServeStateResolver serveStateResolver,
            QdrantVectorStore vectorStore) {
        this.properties = properties;
        this.documentRepository = documentRepository;
        this.serveStateResolver = serveStateResolver;
        this.vectorStore = vectorStore;
    }

    public void syncDocument(UUID documentId) {
        if (!properties.indexing().enabled()) {
            return;
        }
        documentRepository.findById(documentId).ifPresent(this::syncDocumentEntity);
    }

    public void syncDocumentEntity(DocumentEntity document) {
        if (!properties.indexing().enabled()) {
            return;
        }
        syncDocumentEntityInternal(document);
    }

    private void syncDocumentEntityInternal(DocumentEntity document) {
        CorpusEntity corpus = document.getCorpus();
        if (corpus == null) {
            return;
        }
        DocumentServeState state = serveStateResolver.resolve(document);
        String collectionName = VectorCollectionNaming.collectionForName(corpus.getName());
        if (state == DocumentServeState.DROPPED) {
            vectorStore.deleteByDocumentId(collectionName, document.getId());
            log.debug("removed dropped document {} from {}", document.getId(), collectionName);
            return;
        }
        vectorStore.syncDocumentMetadata(collectionName, document.getId(), document, state);
    }

    public void syncByUrlHash(String urlHash) {
        if (!properties.indexing().enabled()) {
            return;
        }
        for (DocumentEntity document : documentRepository.findByUrlHash(urlHash)) {
            syncDocumentEntity(document);
        }
    }
}
