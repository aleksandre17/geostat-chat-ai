package com.geostat.chat.infrastructure.query;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Shared cache key derivation for intent classification (sha256(normalized|locale)). */
final class QueryIntentCacheKeys {

    private QueryIntentCacheKeys() {}

    static String hash(String normalized, String locale) {
        String raw = (normalized == null ? "" : normalized.strip().toLowerCase())
                + "|"
                + (locale == null ? "" : locale.toLowerCase());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return raw;
        }
    }
}
