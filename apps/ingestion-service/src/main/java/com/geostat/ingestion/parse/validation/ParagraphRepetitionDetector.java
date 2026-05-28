package com.geostat.ingestion.parse.validation;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.DocumentValidator;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.ValidationResult;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class ParagraphRepetitionDetector implements DocumentValidator {

    private final int minParaLen;

    public ParagraphRepetitionDetector(
            @Value("${geostat.ingestion.validation.min-para-len:25}") int minParaLen) {
        this.minParaLen = minParaLen;
    }

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        if (doc.bodyText() == null || doc.bodyText().isBlank()) {
            return ValidationResult.ok();
        }
        String[] paragraphs = doc.bodyText().split("[\\r\\n]{2,}");
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        for (String p : paragraphs) {
            String key = p.strip().toLowerCase().replaceAll("\\s+", " ");
            if (key.length() < minParaLen) {
                seen.add(p.strip());
                continue;
            }
            if (!seen.add(key)) {
                duplicates++;
            }
        }
        if (duplicates > 0) {
            String fixed = String.join("\n\n", seen);
            return ValidationResult.fixed(
                    "paragraph_duplicates_removed", duplicates + " duplicates removed", doc.withBodyText(fixed));
        }
        return ValidationResult.ok();
    }
}
