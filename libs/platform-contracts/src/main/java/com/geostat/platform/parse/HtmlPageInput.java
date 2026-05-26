package com.geostat.platform.parse;

/** Raw HTML page input for content extraction (no Jsoup dependency in contracts). */
public record HtmlPageInput(String html, String canonicalUrl) {

    public HtmlPageInput {
        html = html == null ? "" : html;
        canonicalUrl = canonicalUrl == null ? "" : canonicalUrl;
    }
}
