package com.geostat.ingestion.parse.validation;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.DocumentValidator;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class TruncationDetector implements DocumentValidator {

    private final int minLength;
    private final int lookBack;

    public TruncationDetector(
            @Value("${geostat.ingestion.validation.truncation-min-length:200}") int minLength,
            @Value("${geostat.ingestion.validation.truncation-look-back:40}") int lookBack) {
        this.minLength = minLength;
        this.lookBack = lookBack;
    }

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        String body = doc.bodyText();
        if (body == null || body.length() < minLength) {
            return ValidationResult.ok();
        }
        String tail = body.substring(body.length() - Math.min(lookBack, body.length()));
        boolean endsWithPunctuation = tail.matches(".*[.!?:;\u00bb\\])\"]\\s*$");
        boolean endsWithShortWord = tail.matches(".*\\s+\\S{1,4}\\s*$");
        if (!endsWithPunctuation && !endsWithShortWord) {
            return ValidationResult.degrade("truncated_text", "ends abruptly: ..." + tail.strip());
        }
        return ValidationResult.ok();
    }
}
