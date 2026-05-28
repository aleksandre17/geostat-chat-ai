package com.geostat.ingestion.parse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.QualityThresholds;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThresholdsCorpusQualityGateTest {

    private final ThresholdsCorpusQualityGate gate = new ThresholdsCorpusQualityGate();
    private final QualityThresholds thresholds = QualityThresholds.p0Defaults();

    @Test
    void rejectsBodyShorterThanThirtyChars() {
        CleanedDocument doc = doc("short", 1, 0);
        assertThat(gate.evaluate(doc, thresholds)).isEqualTo(CorpusQualityGate.Decision.SKIP_TOO_SHORT);
    }

    @Test
    void acceptsBodyAtThirtyChars() {
        CleanedDocument doc = doc("x".repeat(30), 1, 0);
        assertThat(gate.evaluate(doc, thresholds)).isEqualTo(CorpusQualityGate.Decision.ACCEPT);
    }

    @Test
    void rejectsBoilerplateOnlyDocument() {
        CleanedDocument doc = doc("adapted version of the website only", 2, 2);
        assertThat(gate.evaluate(doc, thresholds)).isEqualTo(CorpusQualityGate.Decision.SKIP_BOILERPLATE_ONLY);
    }

    @Test
    void acceptsWhenBoilerplateShareBelowHalf() {
        CleanedDocument doc = doc("Real statistical content about inflation trends in Georgia.", 4, 1);
        assertThat(gate.evaluate(doc, thresholds)).isEqualTo(CorpusQualityGate.Decision.ACCEPT);
    }

    private static CleanedDocument doc(String body, int totalParagraphs, int boilerplateParagraphs) {
        return new CleanedDocument(
                "title",
                body,
                "en",
                List.of(),
                null,
                null,
                null,
                totalParagraphs,
                boilerplateParagraphs,
                "",
                null,
                null,
                null);
    }
}
