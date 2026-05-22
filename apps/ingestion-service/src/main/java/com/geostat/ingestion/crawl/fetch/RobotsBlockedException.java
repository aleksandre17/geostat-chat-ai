package com.geostat.ingestion.crawl.fetch;

public class RobotsBlockedException extends Exception {

    public RobotsBlockedException(String url) {
        super("robots.txt disallows fetch: " + url);
    }
}
