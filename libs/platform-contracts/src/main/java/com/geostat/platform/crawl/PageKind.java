package com.geostat.platform.crawl;

/**
 * Well-known page kind identifiers for geostat-portal corpus.
 *
 * <p>Not an enum — new kinds are defined in YAML ({@code pageKindRules}) without any Java change.
 * Java constants exist only for code that must switch on known kinds (e.g. extraction strategies).
 */
public final class PageKind {
    public static final String NEWS        = "news";
    public static final String DATASET     = "dataset";
    public static final String PUBLICATION = "publication";
    public static final String PORTAL      = "portal";
    public static final String CATEGORY    = "category";
    public static final String INFOGRAPHIC = "infographic";
    public static final String UNKNOWN     = "unknown";

    private PageKind() {}
}
