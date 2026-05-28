package com.geostat.ingestion.crawl.policy;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CorpusPolicy {

    public static final String CRAWL_MODE_FULL_SITE = "full-site";
    public static final String CRAWL_MODE_BOUNDED = "bounded";

    private static final int DEFAULT_RATE_LIMIT_MS = 500;
    private static final int DEFAULT_WORKER_THREADS = 5;
    /** Smoke / dev default when policy omits explicit limits. */
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int DEFAULT_MAX_PAGES = 50;
    /** Full-site crawl depth cap (link discovery). */
    private static final int FULL_SITE_MAX_DEPTH = 12;

    private CorpusPolicy() {}

    public static int rateLimitMs(CorpusEntity corpus) {
        return intOrDefault(corpus.getPolicy(), "rateLimitMs", DEFAULT_RATE_LIMIT_MS);
    }

    public static int workerThreads(CorpusEntity corpus) {
        Map<String, Object> policy = corpus.getPolicy();
        Object crawl = policy.get("crawl");
        if (crawl instanceof Map<?, ?> crawlSection) {
            Object value = crawlSection.get("workerThreads");
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return intOrDefault(policy, "workerThreads", DEFAULT_WORKER_THREADS);
    }

    public static int maxDepth(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("maxDepth");
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return isFullSite(corpus) ? FULL_SITE_MAX_DEPTH : DEFAULT_MAX_DEPTH;
    }

    public static int maxPagesPerRun(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("maxPagesPerRun");
        if (value instanceof Number number) {
            int pages = number.intValue();
            if (pages <= 0) {
                return Integer.MAX_VALUE;
            }
            return pages;
        }
        return isFullSite(corpus) ? Integer.MAX_VALUE : DEFAULT_MAX_PAGES;
    }

    public static boolean isFullSite(CorpusEntity corpus) {
        Object mode = corpus.getPolicy().get("crawlMode");
        return CRAWL_MODE_FULL_SITE.equals(mode);
    }

    public static boolean autoContinue(CorpusEntity corpus) {
        Object value = corpus.getPolicy().get("autoContinue");
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return isFullSite(corpus);
    }

    public static boolean respectRobotsTxt(CorpusEntity corpus) {
        Map<String, Object> policy = corpus.getPolicy();
        if (policy == null) {
            return true;
        }
        Object crawl = policy.get("crawl");
        if (crawl instanceof Map<?, ?> crawlSection) {
            Object nested = crawlSection.get("respectRobotsTxt");
            if (nested instanceof Boolean enabled) {
                return enabled;
            }
        }
        Object value = policy.get("respectRobotsTxt");
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return true;
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
