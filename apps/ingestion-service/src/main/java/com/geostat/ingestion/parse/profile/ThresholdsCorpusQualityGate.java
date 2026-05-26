package com.geostat.ingestion.parse.profile;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.QualityThresholds;
import org.springframework.stereotype.Component;

@Component
public class ThresholdsCorpusQualityGate implements CorpusQualityGate {

    @Override
    public Decision evaluate(CleanedDocument doc, QualityThresholds thresholds) {
        if (doc == null || doc.bodyText() == null || doc.bodyText().isBlank()) {
            return Decision.SKIP_TOO_SHORT;
        }
        if (doc.bodyLength() < thresholds.minBodyChars()) {
            return Decision.SKIP_TOO_SHORT;
        }
        if (doc.totalParagraphs() > 0) {
            double ratio = (double) doc.boilerplateParagraphs() / doc.totalParagraphs();
            if (ratio > thresholds.maxBoilerplateParagraphRatio()) {
                return Decision.SKIP_BOILERPLATE_ONLY;
            }
        }
        if (doc.boilerplateParagraphs() > 0 && doc.boilerplateParagraphs() == doc.totalParagraphs()) {
            return Decision.SKIP_BOILERPLATE_ONLY;
        }
        return Decision.ACCEPT;
    }
}
