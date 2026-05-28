package com.geostat.platform.crawl;

import java.util.Map;

/**
 * Network-level fetch configuration for a corpus crawl.
 *
 * <p>All fields are optional — defaults match production-safe behavior.
 * Loaded from the {@code network} section of {@code *-policy.yaml}.
 *
 * <p>Use {@link #defaults()} for production corpora where no network overrides are needed.
 */
public record NetworkPolicy(
        boolean tlsVerify,
        boolean respectRobotsTxt,
        BasicAuthCredential basicAuth,
        Map<String, String> dnsOverrides,
        String userAgent) {

    public NetworkPolicy {
        dnsOverrides = dnsOverrides == null ? Map.of() : Map.copyOf(dnsOverrides);
    }

    /** Production-safe defaults: TLS on, robots.txt respected, no auth, no DNS overrides. */
    public static NetworkPolicy defaults() {
        return new NetworkPolicy(true, true, null, Map.of(), null);
    }

    public boolean hasBasicAuth() {
        return basicAuth != null;
    }

    public boolean hasDnsOverrides() {
        return !dnsOverrides.isEmpty();
    }
}
