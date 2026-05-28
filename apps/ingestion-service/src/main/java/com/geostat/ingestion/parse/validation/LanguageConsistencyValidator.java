package com.geostat.ingestion.parse.validation;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.DocumentValidator;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class LanguageConsistencyValidator implements DocumentValidator {

    private final String configuredFallback;

    public LanguageConsistencyValidator(
            @Value("${geostat.ingestion.validation.language-fallback:ka}") String configuredFallback) {
        this.configuredFallback = configuredFallback;
    }

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        if (doc.language() != null && !doc.language().isBlank()) {
            return ValidationResult.ok();
        }
        String fallback = (profile.language() != null && profile.language().defaultFallback() != null)
                ? profile.language().defaultFallback()
                : configuredFallback;
        return ValidationResult.fixed(
                "language_null_fallback", "set to defaultFallback=" + fallback, doc.withLanguage(fallback));
    }
}
