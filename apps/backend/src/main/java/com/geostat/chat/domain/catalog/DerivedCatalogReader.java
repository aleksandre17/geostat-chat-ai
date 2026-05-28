package com.geostat.chat.domain.catalog;

import java.util.List;
import java.util.UUID;

/** Port — read derived catalog links from Postgres materialized views. */
public interface DerivedCatalogReader {

    DerivedClusterMatch matchClusters(String query, String language, int limit);

    default List<DerivedTopicCluster> matchTopicClusterLabels(String query, String language, int limit) {
        return matchClusters(query, language, limit).clusters();
    }

    default List<UUID> matchTopicClusters(String query, String language, int limit) {
        return matchClusters(query, language, limit).clusterIds();
    }

    List<DerivedCatalogLink> findPortalLinks(List<UUID> topicClusterIds, String language);

    List<DerivedCatalogLink> findSpecificLinks(List<UUID> topicClusterIds, String language, int maxRank);

    List<DerivedCatalogLink> findTopPortalLinks(String language, int limit);

    List<DerivedTopicCluster> loadClusterLabels(List<UUID> clusterIds);
}
