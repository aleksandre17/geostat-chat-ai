package com.geostat.platform.parse;

public interface DocumentValidator {
    ValidationResult validate(CleanedDocument doc, ParseProfile profile);
}
