package com.geostat.ingestion.enrichment.pagekind;

import java.util.Set;

public final class PageKindValues {

    public static final String PORTAL = "portal";
    public static final String DATASET = "dataset";
    public static final String REPORT = "report";
    public static final String NEWS = "news";
    public static final String FAQ = "faq";
    public static final String NAVIGATION = "navigation";
    public static final String UNKNOWN = "unknown";

    public static final Set<String> ALLOWED =
            Set.of(PORTAL, DATASET, REPORT, NEWS, FAQ, NAVIGATION, UNKNOWN);

    private PageKindValues() {}
}
