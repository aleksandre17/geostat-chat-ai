package com.geostat.ingestion.parse;

import java.net.URI;

/** Infer ka/en from geostat.ge URL path when html lang is missing. */
public final class UrlLocaleInferer {

    private UrlLocaleInferer() {}

    public static String infer(String url, String htmlLang) {
        if (htmlLang != null && !htmlLang.isBlank()) {
            return htmlLang.split("-")[0].toLowerCase();
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(url.strip()).getPath();
            if (path == null) {
                return null;
            }
            if (path.startsWith("/en") || path.contains("/en/")) {
                return "en";
            }
            if (path.startsWith("/ka") || path.contains("/ka/")) {
                return "ka";
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }
}
