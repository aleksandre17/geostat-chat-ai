package com.geostat.ingestion.parse.validation;

import java.util.List;

/** Maps validation violation strings to stable DB codes for quality gate SQL. */
public final class ValidationViolationCodes {

    private ValidationViolationCodes() {}

    public static String[] toArray(List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            return new String[0];
        }
        return violations.stream().map(ValidationViolationCodes::toCode).distinct().toArray(String[]::new);
    }

    static String toCode(String violation) {
        if (violation == null || violation.isBlank()) {
            return violation;
        }
        int colon = violation.indexOf(':');
        return colon > 0 ? violation.substring(0, colon).strip() : violation.strip();
    }
}
