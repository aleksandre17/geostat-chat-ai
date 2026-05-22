package com.geostat.ingestion.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.geostat.ingestion.chunk.strategy.FixedSizeChunker;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.ChunkRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentChunkWriterTest {

    @Mock
    private ChunkRepository chunkRepository;

    @Spy
    private FixedSizeChunker chunker = new FixedSizeChunker();

    @InjectMocks
    private DocumentChunkWriter writer;

    @Test
    void replaceChunksDeletesThenSaves() {
        DocumentEntity document = new DocumentEntity();
        document.setId(UUID.randomUUID());
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());

        int count = writer.replaceChunks(document, corpus, "First paragraph. " + "word ".repeat(200));

        assertThat(count).isGreaterThan(0);
        verify(chunkRepository).deleteByDocument_Id(document.getId());
        verify(chunkRepository, atLeastOnce()).save(any());
    }
}
