package com.geostat.ingestion.crawl.fetch;

import com.geostat.ingestion.crawl.EncodingMismatchDetector;
import com.geostat.platform.crawl.BasicAuthCredential;
import com.geostat.platform.crawl.NetworkPolicy;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Conditional GET (If-None-Match / If-Modified-Since) for incremental freshness. */
@Component
class ConditionalHttpFetcher {

    private static final Logger log = LoggerFactory.getLogger(ConditionalHttpFetcher.class);
    private static final int DEFAULT_TIMEOUT_MS = 10_000;

    FetchedPage fetch(String url, String userAgent, FetchOptions options) throws IOException, InterruptedException {
        return fetch(url, userAgent, options, NetworkPolicy.defaults(), DEFAULT_TIMEOUT_MS);
    }

    FetchedPage fetch(String url, String userAgent, FetchOptions options, NetworkPolicy network, int timeoutMs)
            throws IOException, InterruptedException {
        HttpClient client = buildClient(network, timeoutMs);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,*/*");
        if (options.ifNoneMatch() != null && !options.ifNoneMatch().isBlank()) {
            builder.header("If-None-Match", options.ifNoneMatch());
        } else if (options.ifModifiedSince() != null && !options.ifModifiedSince().isBlank()) {
            builder.header("If-Modified-Since", options.ifModifiedSince());
        }

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status == 304) {
            String finalUrl = response.uri().toString();
            return new FetchedPage(url, finalUrl, 304, null, null, null, null, null, false);
        }
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " for " + url);
        }

        String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
        String finalUrl = response.uri().toString();
        boolean encodingIssue = false;
        if (EncodingMismatchDetector.looksCorrupted(finalUrl, body)) {
            String contentType = response.headers().firstValue("content-type").orElse(null);
            log.warn(
                    "[encoding] Possible encoding mismatch: /ka/ page with no Georgian chars. url={} Content-Type={}",
                    finalUrl,
                    contentType);
            // Document will be REJECTED by LanguageConsistencyValidator — that's correct.
            // This WARN allows operators to investigate the server config.
            encodingIssue = true;
        }
        Document document = Jsoup.parse(body, finalUrl);
        String responseEtag = response.headers().firstValue("etag").orElse(null);
        String responseLastModifiedHttp = response.headers().firstValue("last-modified").orElse(null);
        Instant responseLastModified = parseHttpDate(responseLastModifiedHttp);
        return new FetchedPage(
                url,
                finalUrl,
                status,
                document,
                responseEtag,
                responseLastModified,
                responseLastModifiedHttp,
                responseEtag,
                encodingIssue);
    }

    private static HttpClient buildClient(NetworkPolicy network, int timeoutMs) {
        NetworkPolicy effective = network != null ? network : NetworkPolicy.defaults();
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(effectiveTimeout));
        if (!effective.tlsVerify()) {
            builder.sslContext(permissiveSslContext());
        }
        if (effective.hasBasicAuth()) {
            BasicAuthCredential auth = effective.basicAuth();
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(auth.username(), auth.password().toCharArray());
                }
            });
        }
        return builder.build();
    }

    private static SSLContext permissiveSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build permissive SSL context", e);
        }
    }

    private static Instant parseHttpDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.from(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
        } catch (Exception e) {
            return null;
        }
    }
}
