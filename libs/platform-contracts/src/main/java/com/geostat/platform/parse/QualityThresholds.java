package com.geostat.platform.parse;

/** Acceptance thresholds for Layer 1 corpus quality gate. */
public record QualityThresholds(
        int minBodyChars,
        double maxBoilerplateParagraphRatio,
        double maxBoilerplateBodyRatio) {

    public static QualityThresholds p0Defaults() {
        return new QualityThresholds(30, 0.49, 0.95);
    }
}
