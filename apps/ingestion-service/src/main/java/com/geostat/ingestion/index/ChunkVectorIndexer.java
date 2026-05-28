package com.geostat.ingestion.index;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.ingestion.index.lifecycle.DocumentServeState;
import com.geostat.ingestion.index.lifecycle.DocumentServeStateResolver;
import com.geostat.ingestion.index.qdrant.QdrantCollectionManager;
import com.geostat.qdrant.QdrantOperationException;
import com.geostat.qdrant.VectorCollectionNaming;
import com.geostat.ingestion.index.qdrant.QdrantVectorStore;
import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.VectorIndexEntity;
import com.geostat.ingestion.persistence.repository.ChunkRepository;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.VectorIndexRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
public class ChunkVectorIndexer {

    private static final Logger log = LoggerFactory.getLogger(ChunkVectorIndexer.class);

    private final IngestionProperties properties;
    private final EmbeddingPort embedding;
    private final DocumentRepository documentRepository;
    private final CorpusRepository corpusRepository;
    private final ChunkRepository chunkRepository;
    private final VectorIndexRepository vectorIndexRepository;
    private final QdrantCollectionManager collectionManager;
    private final QdrantVectorStore vectorStore;
    private final DocumentServeStateResolver serveStateResolver;
    private final JdbcTemplate jdbcTemplate;

    public ChunkVectorIndexer(
            IngestionProperties properties,
            EmbeddingPort embedding,
            DocumentRepository documentRepository,
            CorpusRepository corpusRepository,
            ChunkRepository chunkRepository,
            VectorIndexRepository vectorIndexRepository,
            QdrantCollectionManager collectionManager,
            QdrantVectorStore vectorStore,
            DocumentServeStateResolver serveStateResolver,
            JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.embedding = embedding;
        this.documentRepository = documentRepository;
        this.corpusRepository = corpusRepository;
        this.chunkRepository = chunkRepository;
        this.vectorIndexRepository = vectorIndexRepository;
        this.collectionManager = collectionManager;
        this.vectorStore = vectorStore;
        this.serveStateResolver = serveStateResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int indexDocument(UUID documentId, UUID corpusId) {
        if (!properties.indexing().enabled()) {
            return 0;
        }
        try {
            return indexDocumentInternal(documentId, corpusId);
        } catch (QdrantOperationException e) {
            log.warn("Qdrant indexing failed for document {}: {}", documentId, e.getMessage());
            return 0;
        } catch (RuntimeException e) {
            log.warn("vector indexing failed for document {}: {}", documentId, e.getMessage());
            return 0;
        }
    }

    int indexDocumentInternal(UUID documentId, UUID corpusId) {
        DocumentEntity document = documentRepository.findById(documentId).orElseThrow();
        CorpusEntity corpus = corpusRepository.findById(corpusId).orElseThrow();
        List<ChunkEntity> chunks =
                chunkRepository.findByDocument_IdOrderBySequenceNoAsc(documentId);
        if (chunks.isEmpty()) {
            return 0;
        }

        DocumentServeState serveState = serveStateResolver.resolve(document);
        if (serveState == DocumentServeState.DROPPED) {
            String collectionName = VectorCollectionNaming.collectionForName(corpus.getName());
            vectorStore.deleteByDocumentId(collectionName, documentId);
            return 0;
        }

        String collectionName = VectorCollectionNaming.collectionForName(corpus.getName());
        String indexVersion = properties.indexing().indexVersion();
        collectionManager.ensureCollection(collectionName, embedding.dimensions());

        vectorStore.deleteByDocumentId(collectionName, documentId);

        List<String> texts = chunks.stream().map(ChunkEntity::getText).toList();
        float[][] vectors = embedding.embedBatch(texts);
        String modelId = properties.embedding().modelId();

        vectorStore.upsert(collectionName, chunks, document, corpus, vectors, indexVersion, serveState);

        vectorIndexRepository.deleteByDocumentId(documentId);

        List<VectorIndexEntity> vectorIndexEntities = new ArrayList<>(chunks.size());
        for (ChunkEntity chunk : chunks) {
            VectorIndexEntity index = new VectorIndexEntity();
            index.setChunk(chunk);
            index.setCollectionName(collectionName);
            index.setPointId(chunk.getId().toString());
            index.setIndexVersion(indexVersion);
            index.setEmbeddingModel(modelId);
            vectorIndexEntities.add(index);
        }
        vectorIndexRepository.saveAll(vectorIndexEntities);

        jdbcTemplate.update(
                """
                UPDATE ingestion.chunk
                SET embedding_status = 'embedded',
                    embedding_model  = ?
                WHERE document_id = ?
                """,
                modelId,
                documentId);

        log.debug(
                "indexed {} chunks for document {} into collection {}",
                chunks.size(),
                documentId,
                collectionName);
        return chunks.size();
    }
}
