package com.geostat.chat.domain.catalog;

import java.util.UUID;

/**
 * A topic cluster matched to the current query, with score, labels (for diversity grouping),
 * and match reason (for observability/debugging).
 */
public record ScoredCluster(
        UUID   clusterId,
        double score,
        String labelKa,
        String labelEn,
        String matchReason
) {}
