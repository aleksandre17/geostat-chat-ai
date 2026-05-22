package com.geostat.ingestion.crawl.fetch;

import org.jsoup.nodes.Document;

public record FetchedPage(String url, int statusCode, Document html) {}
