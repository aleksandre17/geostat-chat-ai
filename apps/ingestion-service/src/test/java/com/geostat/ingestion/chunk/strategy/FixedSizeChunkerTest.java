package com.geostat.ingestion.chunk.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void chunk_splitsAtParagraphBoundary_preferredOverWordBoundary() {
        String text = "ბუნებრივი მოძრაობა 2024 წელს შეადგინა 1.2 ათასი ადამიანი." +
                "\n\n" +
                "GDP გაიზარდა 8.7%-ით 2024 წელს — უმაღლესი 2019 წლის შემდეგ.";
        List<TextChunk> chunks = chunker.chunk(text, 80, 15);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(1).text()).doesNotStartWith("ით");
        assertThat(chunks.get(1).text()).startsWith("GDP");
    }

    @Test
    void chunk_fallsBackToWordBoundary_whenNoParagraphBreakNearEnd() {
        String text = "a".repeat(400) + " " + "b".repeat(400);
        List<TextChunk> chunks = chunker.chunk(text, 500, 50);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(1).text()).startsWith("b");
    }

    @Test
    void chunk_shortText_returnsSingleChunk() {
        String text = "GDP 8.7%.\n\nMovement 1.2%.";
        assertThat(chunker.chunk(text, 800, 120)).hasSize(1);
    }

    @Test
    void chunk_preservesStatisticalContent_intactWithinChunk() {
        String gdp = "GDP გაიზარდა 8.7%-ით 2024 წელს.";
        String pop = "მოსახლეობა შეადგინა 3.7 მლნ. ადამიანი.";
        String text = gdp + "\n\n" + pop;
        List<TextChunk> chunks = chunker.chunk(text, 50, 10);
        boolean gdpComplete = chunks.stream().anyMatch(c -> c.text().contains("8.7%"));
        boolean popComplete = chunks.stream().anyMatch(c -> c.text().contains("3.7 მლნ."));
        assertThat(gdpComplete).isTrue();
        assertThat(popComplete).isTrue();
    }
}
