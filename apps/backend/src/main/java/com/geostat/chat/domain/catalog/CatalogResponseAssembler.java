package com.geostat.chat.domain.catalog;

import com.geostat.chat.domain.query.AnalyzedQuery;
import java.util.List;

/** Assembles derived/yaml catalog labels + link cards for one chat turn. */
public interface CatalogResponseAssembler {

    record Bundle(CatalogTopicLabelResolver.Labels topicLabels, List<LinkCard> links) {}

    Bundle assemble(List<Topic> detectedTopics, String query, String language, boolean georgian);

    /** Assemble using enriched AnalyzedQuery when available. Falls back to normalized text. */
    default Bundle assemble(List<Topic> detectedTopics, AnalyzedQuery analyzed,
                             String language, boolean georgian) {
        String queryText = resolveCatalogQueryText(analyzed);
        return assemble(detectedTopics, queryText, language, georgian);
    }

    /** Prefer spell-fixed + expanded retrieval text; fall back to normalized query. */
    static String resolveCatalogQueryText(AnalyzedQuery analyzed) {
        if (analyzed == null) {
            return "";
        }
        if (analyzed.retrievalText() != null && !analyzed.retrievalText().isBlank()) {
            return analyzed.retrievalText();
        }
        return analyzed.normalized() != null ? analyzed.normalized() : "";
    }

    List<LinkCard> buildPortalLinks(boolean georgian);
}
