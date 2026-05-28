package com.geostat.ingestion.chunk;



import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.argThat;

import static org.mockito.Mockito.never;

import static org.mockito.Mockito.times;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;



import com.geostat.ingestion.chunk.strategy.FixedSizeChunker;

import com.geostat.ingestion.persistence.entity.ChunkEntity;

import com.geostat.ingestion.persistence.entity.CorpusEntity;

import com.geostat.ingestion.persistence.entity.DocumentEntity;

import com.geostat.ingestion.persistence.repository.ChunkRepository;

import java.util.List;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;

import org.mockito.Spy;

import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.junit.jupiter.MockitoSettings;

import org.mockito.quality.Strictness;



@ExtendWith(MockitoExtension.class)

@MockitoSettings(strictness = Strictness.LENIENT)

class DocumentChunkWriterTest {



    @Mock

    private ChunkRepository chunkRepository;



    @Spy

    private FixedSizeChunker chunker = new FixedSizeChunker();

    @Spy

    private ChunkHasher chunkHasher = new ChunkHasher();



    private DocumentChunkWriter writer;



    @BeforeEach

    void setUp() {

        writer = new DocumentChunkWriter(chunkRepository, chunker, chunkHasher);

        when(chunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    }



    @Test

    void replaceChunksDeletesThenInserts() {

        DocumentEntity document = new DocumentEntity();

        document.setId(UUID.randomUUID());

        CorpusEntity corpus = new CorpusEntity();

        corpus.setId(UUID.randomUUID());



        int count = writer.replaceChunks(document, corpus, "First paragraph. " + "word ".repeat(200));



        assertThat(count).isGreaterThan(0);

        verify(chunkRepository).deleteByDocumentId(document.getId());

        verify(chunkRepository, times(1)).saveAll(argThat(list -> {
            List<?> chunks = (List<?>) list;
            return !chunks.isEmpty()
                    && chunks.stream()
                            .allMatch(c -> ((ChunkEntity) c).getContentHash() != null
                                    && !((ChunkEntity) c).getContentHash().isBlank());
        }));

    }



    @Test

    void replaceChunks_callsSaveAll_notSavePerChunk() {

        DocumentEntity document = new DocumentEntity();

        document.setId(UUID.randomUUID());

        CorpusEntity corpus = new CorpusEntity();

        corpus.setId(UUID.randomUUID());

        String longText = "First paragraph. " + "word ".repeat(200);



        int count = writer.replaceChunks(document, corpus, longText, List.of(), "ka");



        assertThat(count).isGreaterThan(0);

        verify(chunkRepository, times(1)).saveAll(argThat(list -> ((List<?>) list).size() == count));

        verify(chunkRepository, never()).save(any(ChunkEntity.class));

    }

}

