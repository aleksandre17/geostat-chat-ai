package com.geostat.ingestion.chunk.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker();

    @Test
    void emptyTextProducesNoChunks() {
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
    }

    @Test
    void shortTextIsSingleChunk() {
        assertThat(chunker.chunk("Hello world"))
                .containsExactly(new TextChunk(0, "Hello world"));
    }

    @Test
    void longTextSplitsWithOverlap() {
        String text = "word ".repeat(300).trim();
        var chunks = chunker.chunk(text, 200, 40);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).sequenceNo()).isZero();
        assertThat(chunks.get(1).sequenceNo()).isEqualTo(1);
        assertThat(chunks.get(0).text().length()).isLessThanOrEqualTo(200);
    }
}
