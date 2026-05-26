package com.geostat.ingestion.catalog.refresh;

import java.util.List;

public final class CatalogMaterializedViews {

    public static final String PORTAL_LINK = "ingestion.mv_portal_link";
    public static final String SPECIFIC_LINK = "ingestion.mv_specific_link";
    public static final String TOPIC_KEYWORDS = "ingestion.mv_topic_keywords";

    public static final List<String> ALL = List.of(PORTAL_LINK, SPECIFIC_LINK, TOPIC_KEYWORDS);

    private CatalogMaterializedViews() {}

    public static String refreshConcurrentlySql(String viewName) {
        return "REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName;
    }
}
