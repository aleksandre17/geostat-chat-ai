package com.geostat.ingestion.crawl.policy;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CorpusPolicy {

    private static final int DEFAULT_RATE_LIMIT_MS = 500;
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int DEFAULT_MAX_PAGES = 50;

    private CorpusPolicy() {}

    public static int rateLimitMs(CorpusEntity corpus) {
        return intOrDefault(corpus.getPolicy(), "rateLimitMs", DEFAULT_RATE_LIMIT_MS);
    }

    public static int maxDepth(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("maxDepth");
        if (value == null) {
            return DEFAULT_MAX_DEPTH;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_MAX_DEPTH;
    }

    public static int maxPagesPerRun(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("maxPagesPerRun");
        if (value == null) {
            return DEFAULT_MAX_PAGES;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_MAX_PAGES;
    }

    public static boolean respectRobotsTxt(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("respectRobotsTxt");
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return false;
    }

    public static List<String> allowedHosts(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("allowedHosts");
        if (value instanceof List<?> list) {
            List<String> hosts = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    hosts.add(item.toString().toLowerCase());
                }
            }
            if (!hosts.isEmpty()) {
                return hosts;
            }
        }
        return List.of();
    }

    public static List<String> excludePatterns(CorpusEntity corpus) {
        return stringPatterns(corpus.getPolicy().get("excludePatterns"));
    }

    public static List<String> includePatterns(CorpusEntity corpus) {
        return stringPatterns(corpus.getPolicy().get("includePatterns"));
    }

    public static boolean isHostAllowed(CorpusEntity corpus, String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        List<String> allowed = allowedHosts(corpus);
        if (allowed.isEmpty()) {
            return true;
        }
        String normalized = host.toLowerCase();
        return allowed.stream().anyMatch(h -> normalized.equals(h) || normalized.endsWith("." + h));
    }

    public static boolean isUrlAllowed(CorpusEntity corpus, String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!isHostAllowed(corpus, uri.getHost())) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        for (String pattern : excludePatterns(corpus)) {
            if (!pattern.isBlank() && path.contains(pattern)) {
                return false;
            }
        }
        List<String> include = includePatterns(corpus);
        if (include.isEmpty()) {
            return true;
        }
        for (String pattern : include) {
            if (!pattern.isBlank() && path.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> stringPatterns(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> patterns = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                patterns.add(item.toString());
            }
        }
        return patterns;
    }

    private static int intOrDefault(Map<String, Object> policy, String key, int defaultValue) {
        Object value = policy.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
