package com.geostat.chat.domain.catalog;

/** L04: locale-aware catalog URL resolution. */
public final class CatalogUrls {

    private CatalogUrls() {}

    public static String localeUrl(String url, boolean isGeorgian) {
        if (url == null || isGeorgian) {
            return url;
        }
        if (url.contains("/ka/")) {
            return url.replace("/ka/", "/en/");
        }
        if (url.endsWith("/ka")) {
            return url.substring(0, url.length() - 2) + "en";
        }
        return url;
    }
}
