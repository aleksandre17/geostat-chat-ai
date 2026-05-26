package com.geostat.chat.domain.catalog;

import java.util.List;
import java.util.UUID;

/** Single cluster-match result for derived catalog (ids + labels, one SQL round-trip). */
public record DerivedClusterMatch(List<DerivedTopicCluster> clusters) {

    public DerivedClusterMatch {
        clusters = clusters == null ? List.of() : List.copyOf(clusters);
    }

    public List<UUID> clusterIds() {
        return clusters.stream().map(DerivedTopicCluster::id).toList();
    }

    public boolean isEmpty() {
        return clusters.isEmpty();
    }
}
