package com.geostat.platform.parse;

public enum SubdomainMode {
    all,
    list,
    none;

    public static SubdomainMode fromYaml(String value) {
        if (value == null || value.isBlank()) {
            return all;
        }
        return valueOf(value.trim().toLowerCase());
    }
}
