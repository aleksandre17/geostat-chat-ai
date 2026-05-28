package com.geostat.platform.url;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 hash of normalized URL or content text for deduplication keys. */
public final class UrlHasher {

    private UrlHasher() {}

    public static String hash(String url) {
        String normalized = normalize(url);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String normalize(String url) {
        return url == null ? "" : url.trim();
    }
}
