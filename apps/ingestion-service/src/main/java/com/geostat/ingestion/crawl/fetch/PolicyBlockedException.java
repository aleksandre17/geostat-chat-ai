package com.geostat.ingestion.crawl.fetch;

public class PolicyBlockedException extends Exception {

    public PolicyBlockedException(String url) {
        super("corpus policy blocks url: " + url);
    }
}
