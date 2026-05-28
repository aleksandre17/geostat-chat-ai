package com.geostat.platform.parse;

import java.util.List;

public record ValidationResult(
        DocumentQuality quality,
        List<String> violations,
        CleanedDocument fixedDoc) {

    public static ValidationResult ok() {
        return new ValidationResult(DocumentQuality.GOOD, List.of(), null);
    }

    public static ValidationResult degrade(String code, String detail) {
        return new ValidationResult(DocumentQuality.DEGRADED, List.of(code + ": " + detail), null);
    }

    public static ValidationResult fixed(String code, String detail, CleanedDocument doc) {
        return new ValidationResult(DocumentQuality.DEGRADED, List.of(code + ": " + detail), doc);
    }

    public static ValidationResult skip(String reason) {
        return new ValidationResult(DocumentQuality.SKIP, List.of("skip: " + reason), null);
    }

    public static ValidationResult reject(String code, String detail) {
        return new ValidationResult(DocumentQuality.REJECT, List.of(code + ": " + detail), null);
    }

    public boolean isRejected() {
        return quality == DocumentQuality.REJECT;
    }

    public boolean isSkip() {
        return quality == DocumentQuality.SKIP;
    }
}
