package com.geostat.ingestion.parse.validation;

import com.geostat.ingestion.enrichment.pagekind.PageKindUrlHeuristic;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.DocumentValidator;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class MinContentLengthValidator implements DocumentValidator {

    private final int rejectBelow;
    private final int degradeBelow;

    public MinContentLengthValidator(
            @Value("${geostat.ingestion.validation.min-content-reject:30}") int rejectBelow,
            @Value("${geostat.ingestion.validation.min-content-degrade:100}") int degradeBelow) {
        this.rejectBelow = rejectBelow;
        this.degradeBelow = degradeBelow;
    }

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        if (doc.isNoindex()) {
            return ValidationResult.reject("noindex_directive", "");
        }
        int len = doc.bodyText() == null ? 0 : doc.bodyText().strip().length();
        if (len < rejectBelow) {
            return ValidationResult.reject("content_too_short", "length=" + len);
        }
        if (len < degradeBelow) {
            return ValidationResult.degrade("content_short", "length=" + len);
        }
        if (doc.pageKind() != null && "portal".equalsIgnoreCase(doc.pageKind())) {
            return ValidationResult.skip("portal_landing_no_statistical_content");
        }
        var portalKind = PageKindUrlHeuristic.classify(
                new DocumentContext(
                        null,
                        doc.canonicalUrl(),
                        doc.title(),
                        doc.bodyText(),
                        doc.language(),
                        doc.sectionPath().isEmpty() ? null : String.join(" > ", doc.sectionPath())),
                "validation");
        if (portalKind.isPresent() && "portal".equalsIgnoreCase(portalKind.get().kind())) {
            return ValidationResult.skip("portal_landing_no_statistical_content");
        }
        return ValidationResult.ok();
    }
}
