package com.geostat.ingestion.parse.reparse;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ingestion.reparse")
public class ReparseProperties {

    private int reparseWorkerThreads = 8;

    public int reparseWorkerThreads() {
        return reparseWorkerThreads;
    }

    public void setReparseWorkerThreads(int reparseWorkerThreads) {
        this.reparseWorkerThreads = reparseWorkerThreads;
    }
}
