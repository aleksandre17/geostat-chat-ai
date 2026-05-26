package com.geostat.chat.domain.catalog;

/** Port for presentation styles loaded from {@code catalog/topic-style.yaml} (RAG spec §17). */
public interface PresentationStyleCatalog {

    PresentationStyle pageKindStyle(String pageKind);

    LinkTypeStyle linkTypeStyle(String linkType);

    String linkTypeLabel(String linkType, boolean isGeorgian);

    /** Maps ingestion {@code page_kind} to {@link LinkCard#type()} for derived catalog cards. */
    String cardTypeForPageKind(String pageKind);
}
