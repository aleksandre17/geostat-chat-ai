package com.geostat.chat.domain.catalog;

public record LinkInfo(
        String url,
        String titleKa,
        String titleEn,
        String urlEn) {

    public LinkInfo(String url, String titleKa, String titleEn) {
        this(url, titleKa, titleEn, CatalogUrls.localeUrl(url, false));
    }

    public String getTitle(boolean isGeorgian) {
        return isGeorgian ? titleKa : titleEn;
    }

    public String resolvedUrl(boolean isGeorgian) {
        if (!isGeorgian && urlEn != null && !urlEn.isBlank()) {
            return urlEn;
        }
        return CatalogUrls.localeUrl(url, isGeorgian);
    }
}
