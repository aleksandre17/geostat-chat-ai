package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.config.RetrievalProperties;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.retrieval.index.VectorCollectionNaming;
import com.geostat.retrieval.index.qdrant.QdrantOperationException;
import com.geostat.retrieval.index.qdrant.QdrantSearchStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QdrantRetrievalService implements RetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantRetrievalService.class);

    private final RetrievalProperties properties;
    private final EmbeddingPort embedding;
    private final QdrantSearchStore searchStore;

    public QdrantRetrievalService(
            RetrievalProperties properties, EmbeddingPort embedding, QdrantSearchStore searchStore) {
        this.properties = properties;
        this.embedding = embedding;
        this.searchStore = searchStore;
    }

    @Override
    public List<RetrievedChunk> search(RetrievalQuery query) {
        if (query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        String collection = resolveCollection(query);
        float[] vector = embedding.embed(query.text());
        try {
            return searchStore.search(collection, vector, query.maxChunks());
        } catch (QdrantOperationException e) {
            log.warn("retrieval search failed for collection {}: {}", collection, e.getMessage());
            return List.of();
        }
    }

    private String resolveCollection(RetrievalQuery query) {
        if (query.corpusName() != null && !query.corpusName().isBlank()) {
            return VectorCollectionNaming.collectionForCorpusName(query.corpusName());
        }
        return properties.defaultCollection();
    }
}
