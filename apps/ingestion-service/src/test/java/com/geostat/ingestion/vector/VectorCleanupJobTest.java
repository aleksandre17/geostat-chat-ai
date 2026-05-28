package com.geostat.ingestion.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.index.qdrant.QdrantVectorStore;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class VectorCleanupJobTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private QdrantVectorStore qdrant;

    @Mock
    private CorpusRepository corpusRepository;

    @InjectMocks
    private VectorCleanupJob job;

    private UUID corpusId;

    @BeforeEach
    void setUp() {
        corpusId = UUID.randomUUID();
    }

    @Test
    void cleanOrphanVectors_deletesEmbeddedChunks_forSkipDocuments() {
        String orphanId = UUID.randomUUID().toString();
        when(jdbc.queryForList(anyString(), eq(String.class), eq(corpusId)))
                .thenReturn(List.of(orphanId));

        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName("geostat-portal");
        when(corpusRepository.findById(corpusId)).thenReturn(Optional.of(corpus));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        VectorCleanupJob.CleanupResult result = job.cleanOrphanVectors(corpusId);

        assertThat(result.deletedCount()).isEqualTo(1);
        verify(qdrant).delete(eq("geostat-portal"), argThat(ids -> ids.size() == 1));
    }

    @Test
    void cleanOrphanVectors_idempotent_onSecondRun() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(corpusId)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        VectorCleanupJob.CleanupResult first = job.cleanOrphanVectors(corpusId);
        VectorCleanupJob.CleanupResult second = job.cleanOrphanVectors(corpusId);

        assertThat(first.deletedCount()).isZero();
        assertThat(second.deletedCount()).isZero();
        verify(qdrant, never()).delete(anyString(), any());
    }

    @Test
    void cleanOrphanVectors_doesNotDelete_goodDocChunks() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(corpusId)))
                .thenReturn(List.of());

        VectorCleanupJob.CleanupResult result = job.cleanOrphanVectors(corpusId);

        assertThat(result.deletedCount()).isZero();
        verifyNoInteractions(qdrant);
    }
}
