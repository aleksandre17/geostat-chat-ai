package com.geostat.chat.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.RetrievalConfidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultConfidenceAssessorTest {

    private final DefaultConfidenceAssessor assessor = new DefaultConfidenceAssessor();

    @Test
    void high_confidence_when_top_score_above_075_with_gap() {
        List<RetrievedChunk> chunks = List.of(
                chunk(0.85f),
                chunk(0.70f));

        RetrievalConfidence result = assessor.assess(chunks);

        assertThat(result).isEqualTo(RetrievalConfidence.HIGH);
    }

    @Test
    void medium_confidence_when_top_score_above_055() {
        List<RetrievedChunk> chunks = List.of(
                chunk(0.65f),
                chunk(0.62f));

        RetrievalConfidence result = assessor.assess(chunks);

        assertThat(result).isEqualTo(RetrievalConfidence.MEDIUM);
    }

    @Test
    void low_confidence_when_top_score_above_035() {
        List<RetrievedChunk> chunks = List.of(
                chunk(0.40f),
                chunk(0.38f));

        RetrievalConfidence result = assessor.assess(chunks);

        assertThat(result).isEqualTo(RetrievalConfidence.LOW);
    }

    @Test
    void none_confidence_when_top_score_below_035() {
        List<RetrievedChunk> chunks = List.of(
                chunk(0.25f));

        RetrievalConfidence result = assessor.assess(chunks);

        assertThat(result).isEqualTo(RetrievalConfidence.NONE);
    }

    @Test
    void none_confidence_for_empty_results() {
        RetrievalConfidence result = assessor.assess(List.of());

        assertThat(result).isEqualTo(RetrievalConfidence.NONE);
    }

    @Test
    void none_confidence_for_null_results() {
        RetrievalConfidence result = assessor.assess(null);

        assertThat(result).isEqualTo(RetrievalConfidence.NONE);
    }

    private static RetrievedChunk chunk(double score) {
        return new RetrievedChunk("doc1", "http://test", "text", score, "ka", "Title", null, null, null);
    }
}
