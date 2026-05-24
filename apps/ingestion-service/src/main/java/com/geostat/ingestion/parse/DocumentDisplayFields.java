package com.geostat.ingestion.parse;

import com.geostat.ingestion.persistence.entity.DocumentEntity;

/** Applies RAG-L11 display metadata columns on {@link DocumentEntity}. */
public final class DocumentDisplayFields {

    private DocumentDisplayFields() {}

    public static void apply(DocumentEntity document, HtmlContentCleaner.CleanedContent cleaned) {
        document.setLeadText(cleaned.leadText());
        document.setMetaDescription(cleaned.metaDescription());
        document.setDisplayDescription(cleaned.displayDescription());
    }
}
