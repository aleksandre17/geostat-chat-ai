package com.geostat.platform.crawl;

/**
 * Basic HTTP authentication credential for a corpus crawl.
 *
 * <p>Password must come from an environment variable (e.g. {@code ${ENV_VAR}}) —
 * never hardcode credentials in YAML or Java source.
 */
public record BasicAuthCredential(String username, String password) {

    public BasicAuthCredential {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("basicAuth.username must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "basicAuth.password must not be blank — use ${ENV_VAR} in YAML");
        }
    }
}
