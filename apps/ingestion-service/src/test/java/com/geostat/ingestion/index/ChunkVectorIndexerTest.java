package com.geostat.ingestion.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.ingestion.index.lifecycle.DocumentServeState;
import com.geostat.ingestion.index.qdrant.QdrantCollectionManager;
import com.geostat.ingestion.index.qdrant.QdrantVectorStore;
import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.ChunkRepository;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.VectorIndexRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkVectorIndexerTest {

    @Mock
    private IngestionProperties properties;

    @Mock
    private EmbeddingPort embedding;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private VectorIndexRepository vectorIndexRepository;

    @Mock
    private QdrantCollectionManager collectionManager;

    @Mock
    private QdrantVectorStore vectorStore;

    @Mock
    private com.geostat.ingestion.index.lifecycle.DocumentServeStateResolver serveStateResolver;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ChunkVectorIndexer indexer;

    private UUID documentId;
    private UUID corpusId;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        corpusId = UUID.randomUUID();
        when(properties.indexing()).thenReturn(new IngestionProperties.Indexing(true, "v1"));
        when(properties.embedding())
                .thenReturn(new IngestionProperties.Embedding(
                        "hash-v1", "hash-v1", 384, "", "", "http://127.0.0.1:11434", "nomic-embed-text"));
        when(embedding.dimensions()).thenReturn(384);
        when(embedding.embedBatch(anyList())).thenAnswer(inv -> {
            List<?> texts = inv.getArgument(0);
            float[][] vectors = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                vectors[i] = new float[] {0.1f, 0.2f, 0.3f};
            }
            return vectors;
        });
        when(serveStateResolver.resolve(any())).thenReturn(DocumentServeState.LIVE);
        when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
    }

    @Test
    void indexesChunksIntoQdrantAndPersistsPointers() {
        DocumentEntity document = new DocumentEntity();
        document.setId(documentId);
        document.setCanonicalUrl("https://example.com/page");

        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName("geostat-portal");

        ChunkEntity chunk = new ChunkEntity();
        chunk.setId(UUID.randomUUID());
        chunk.setText("sample chunk");
        chunk.setSequenceNo(0);
        chunk.setChunkStrategy("fixed-size-v1");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(corpusRepository.findById(corpusId)).thenReturn(Optional.of(corpus));
        when(chunkRepository.findByDocument_IdOrderBySequenceNoAsc(documentId))
                .thenReturn(List.of(chunk));

        int indexed = indexer.indexDocument(documentId, corpusId);

        assertThat(indexed).isEqualTo(1);
        verify(collectionManager).ensureCollection("geostat-portal", 384);
        verify(vectorStore).deleteByDocumentId("geostat-portal", documentId);
        verify(vectorStore)
                .upsert(
                        eq("geostat-portal"),
                        eq(List.of(chunk)),
                        eq(document),
                        eq(corpus),
                        any(),
                        eq("v1"),
                        eq(DocumentServeState.LIVE));
        verify(vectorIndexRepository).saveAll(anyList());
        verify(vectorIndexRepository, never()).save(any());
        verify(embedding).embedBatch(anyList());
        verify(embedding, never()).embed(any());
        verify(chunkRepository, never()).save(any());
    }

    @Test
    void skipsWhenIndexingDisabled() {
        when(properties.indexing()).thenReturn(new IngestionProperties.Indexing(false, "v1"));

        int indexed = indexer.indexDocument(documentId, corpusId);

        assertThat(indexed).isZero();
    }
}
