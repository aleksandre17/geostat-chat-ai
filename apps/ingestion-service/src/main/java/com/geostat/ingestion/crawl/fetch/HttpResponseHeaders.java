package com.geostat.ingestion.crawl.fetch;

import edu.uci.ics.crawler4j.fetcher.PageFetchResult;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.apache.http.Header;

final class HttpResponseHeaders {

    private HttpResponseHeaders() {}

    static String etag(PageFetchResult result) {
        return firstHeader(result, "ETag");
    }

    static String lastModifiedHttp(PageFetchResult result) {
        return firstHeader(result, "Last-Modified");
    }

    static String contentType(PageFetchResult result) {
        return firstHeader(result, "Content-Type");
    }

    static Instant lastModified(PageFetchResult result) {
        String raw = lastModifiedHttp(result);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String firstHeader(PageFetchResult result, String name) {
        if (result == null) {
            return null;
        }
        Header[] headers = result.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        for (Header header : headers) {
            if (header != null && name.equalsIgnoreCase(header.getName())) {
                return header.getValue();
            }
        }
        return null;
    }
}
