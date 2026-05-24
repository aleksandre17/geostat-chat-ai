package com.geostat.ingestion.crawl.fetch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Conditional GET (If-None-Match / If-Modified-Since) for incremental freshness. */
final class ConditionalHttpFetcher {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    FetchedPage fetch(String url, String userAgent, String etag, Instant lastModified)
            throws IOException, InterruptedException, PageNotModifiedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,*/*");
        if (etag != null && !etag.isBlank()) {
            builder.header("If-None-Match", etag);
        }
        if (lastModified != null) {
            builder.header("If-Modified-Since", HTTP_DATE.format(lastModified));
        }

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 304) {
            throw new PageNotModifiedException(url);
        }
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " for " + url);
        }

        String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
        Document document = Jsoup.parse(body, url);
        String responseEtag = response.headers().firstValue("etag").orElse(null);
        Instant responseLastModified = parseHttpDate(response.headers().firstValue("last-modified").orElse(null));
        return new FetchedPage(url, status, document, responseEtag, responseLastModified);
    }

    private static Instant parseHttpDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.from(HTTP_DATE.parse(value));
        } catch (Exception e) {
            return null;
        }
    }
}
