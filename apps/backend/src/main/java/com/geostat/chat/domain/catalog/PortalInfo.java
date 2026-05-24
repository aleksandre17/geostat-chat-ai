package com.geostat.chat.domain.catalog;

public record PortalInfo(
        String url,
        String titleKa,
        String titleEn,
        String descriptionKa,
        String urlEn) {

    public PortalInfo(String url, String titleKa, String titleEn, String descriptionKa) {
        this(url, titleKa, titleEn, descriptionKa, null);
    }

    public String getTitle(boolean isGeorgian) {
        return isGeorgian ? titleKa : titleEn;
    }

    public String resolvedUrl(boolean isGeorgian) {
        if (!isGeorgian && urlEn != null && !urlEn.isBlank()) {
            return urlEn;
        }
        return url;
    }
}