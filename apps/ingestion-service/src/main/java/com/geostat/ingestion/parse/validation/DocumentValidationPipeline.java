package com.geostat.ingestion.parse.validation;

import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.DocumentQuality;
import com.geostat.platform.parse.DocumentValidator;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.ValidationOutcome;
import com.geostat.platform.parse.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentValidationPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationPipeline.class);

    private final List<DocumentValidator> validators;

    public DocumentValidationPipeline(List<DocumentValidator> validators) {
        this.validators = validators;
    }

    public ValidationOutcome validate(CleanedDocument doc, ParseProfile profile) {
        CleanedDocument current = doc;
        List<String> allViolations = new ArrayList<>();
        DocumentQuality worst = DocumentQuality.GOOD;

        for (DocumentValidator v : validators) {
            ValidationResult r = v.validate(current, profile);
            allViolations.addAll(r.violations());
            if (r.quality().ordinal() > worst.ordinal()) {
                worst = r.quality();
            }
            if (r.isRejected()) {
                log.warn("[validation] REJECT url={} reasons={}", doc.canonicalUrl(), r.violations());
                return new ValidationOutcome(doc, DocumentQuality.REJECT, allViolations);
            }
            if (r.fixedDoc() != null) {
                current = r.fixedDoc();
            }
            if (r.isSkip()) {
                break;
            }
        }
        return new ValidationOutcome(current, worst, allViolations);
    }
}
