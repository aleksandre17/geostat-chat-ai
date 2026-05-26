package com.geostat.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.chat.catalog")
public class CatalogProperties {

    /** yaml (default) | derived — flip after eval gate (RAG-U02). */
    private String source = "yaml";

    private String jdbcUrl = "";

    private String jdbcUser = "";

    private String jdbcPassword = "";

    private int maxClusters = 3;

    private int maxSpecificRank = 3;

    private int maxPortalLinks = 2;

    public boolean isDerived() {
        return "derived".equalsIgnoreCase(source);
    }

    public String source() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String jdbcUser() {
        return jdbcUser;
    }

    public void setJdbcUser(String jdbcUser) {
        this.jdbcUser = jdbcUser;
    }

    public String jdbcPassword() {
        return jdbcPassword;
    }

    public void setJdbcPassword(String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
    }

    public int maxClusters() {
        return maxClusters;
    }

    public void setMaxClusters(int maxClusters) {
        this.maxClusters = maxClusters;
    }

    public int maxSpecificRank() {
        return maxSpecificRank;
    }

    public void setMaxSpecificRank(int maxSpecificRank) {
        this.maxSpecificRank = maxSpecificRank;
    }

    public int maxPortalLinks() {
        return maxPortalLinks;
    }

    public void setMaxPortalLinks(int maxPortalLinks) {
        this.maxPortalLinks = maxPortalLinks;
    }
}
