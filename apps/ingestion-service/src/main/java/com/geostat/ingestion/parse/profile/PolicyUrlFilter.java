package com.geostat.ingestion.parse.profile;

import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.SubdomainMode;
import com.geostat.platform.parse.UrlFilter;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PolicyUrlFilter implements UrlFilter {

    @Override
    public boolean shouldEnqueue(String url, CorpusPolicyV2 policy) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (policy.curatedUrls().stream().anyMatch(curated -> curated.equalsIgnoreCase(url.trim()))) {
            return true;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (!hostAllowed(uri.getHost(), policy)) {
            return false;
        }
        return pathAllowed(uri.getPath(), policy);
    }

    static boolean hostAllowed(String host, CorpusPolicyV2 policy) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowed : policy.allowedHosts()) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            String allowedHost = allowed.toLowerCase(Locale.ROOT);
            if (normalized.equals(allowedHost)) {
                return true;
            }
        }
        return switch (policy.subdomainMode()) {
            case all -> policy.allowedHosts().stream()
                    .filter(h -> h != null && !h.isBlank())
                    .map(h -> h.toLowerCase(Locale.ROOT))
                    .anyMatch(h -> normalized.equals(h) || normalized.endsWith("." + h));
            case list -> policy.subdomainAllow().stream()
                    .filter(h -> h != null && !h.isBlank())
                    .map(h -> h.toLowerCase(Locale.ROOT))
                    .anyMatch(normalized::equals);
            case none -> false;
        };
    }

    static boolean pathAllowed(String pathValue, CorpusPolicyV2 policy) {
        String path = pathValue == null ? "" : pathValue;
        for (String pattern : policy.excludePatterns()) {
            if (pattern != null && !pattern.isBlank() && path.contains(pattern)) {
                return false;
            }
        }
        if (policy.includePatterns().isEmpty()) {
            return true;
        }
        for (String pattern : policy.includePatterns()) {
            if (pattern != null && !pattern.isBlank() && path.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
