package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.QueryNormalizer;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

/** RAG-U07b — NFKC + whitespace collapse; lowercase for Latin locale. */
@Component
public class NfkcQueryNormalizer implements QueryNormalizer {

    @Override
    public String normalize(String text, String locale) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String nfkc = Normalizer.normalize(text.strip(), Normalizer.Form.NFKC);
        String collapsed = nfkc.replaceAll("\\s+", " ");
        if (isLatinLocale(locale)) {
            return collapsed.toLowerCase();
        }
        return collapsed;
    }

    private static boolean isLatinLocale(String locale) {
        return locale != null && locale.toLowerCase().startsWith("en");
    }
}
