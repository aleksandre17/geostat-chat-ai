package com.geostat.platform.parse;

import java.util.List;

public record ValidationOutcome(
        CleanedDocument document,
        DocumentQuality quality,
        List<String> violations) {

    public boolean isRejected() {
        return quality == DocumentQuality.REJECT;
    }

    public boolean isSkip() {
        return quality == DocumentQuality.SKIP;
    }

    public boolean shouldPersist() {
        return quality != DocumentQuality.REJECT;
    }
}
