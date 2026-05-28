package com.geostat.chat.infrastructure.catalog;

import com.geostat.chat.domain.catalog.DerivedCatalogLink;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.DerivedTopicCluster;
import java.sql.Array;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class JdbcDerivedCatalogReader implements DerivedCatalogReader {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDerivedCatalogReader(JdbcTemplate catalogJdbcTemplate) {
        this.jdbcTemplate = catalogJdbcTemplate;
    }

    @Override
    public DerivedClusterMatch matchClusters(String query, String language, int limit) {
        List<UUID> clusterIds = queryClusterIds(query, language, limit);
        if (clusterIds.isEmpty()) {
            return new DerivedClusterMatch(List.of());
        }
        return new DerivedClusterMatch(loadClusterLabels(clusterIds));
    }

    private List<UUID> queryClusterIds(String query, String language, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        String normalized = query.strip().toLowerCase();
        return jdbcTemplate.query(
                """
                SELECT cluster_id
                FROM (
                    SELECT tc.id AS cluster_id, MAX(tk.doc_frequency) AS score
                    FROM ingestion.topic_cluster tc
                    JOIN ingestion.mv_topic_keywords tk
                      ON tk.topic_cluster_id = tc.id AND tk.language = ?
                    WHERE tc.approved = true
                      AND length(?) >= 3
                      AND position(lower(tk.keyword) IN ?) > 0
                    GROUP BY tc.id
                    UNION ALL
                    SELECT tc.id AS cluster_id, 1 AS score
                    FROM ingestion.topic_cluster tc
                    WHERE tc.approved = true
                      AND (
                          position(lower(COALESCE(
                              (SELECT co.payload->>'labelKa'
                               FROM ingestion.curation_override co
                               WHERE co.action = 'rename_topic'
                                 AND co.target = tc.id::text
                                 AND (co.expires_at IS NULL OR co.expires_at > now())
                               LIMIT 1),
                              tc.label_ka)) IN ?) > 0
                          OR position(lower(COALESCE(
                              (SELECT co.payload->>'labelEn'
                               FROM ingestion.curation_override co
                               WHERE co.action = 'rename_topic'
                                 AND co.target = tc.id::text
                                 AND (co.expires_at IS NULL OR co.expires_at > now())
                               LIMIT 1),
                              tc.label_en)) IN ?) > 0
                      )
                ) matched
                GROUP BY cluster_id
                ORDER BY MAX(score) DESC
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getObject("cluster_id", UUID.class),
                language,
                normalized,
                normalized,
                normalized,
                normalized,
                limit);
    }

    @Override
    public List<DerivedCatalogLink> findPortalLinks(List<UUID> topicClusterIds, String language) {
        if (topicClusterIds == null || topicClusterIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT canonical_url, title, summary, authority_score
                FROM ingestion.mv_portal_link
                WHERE language = ?
                  AND topic_cluster_id = ANY(?)
                ORDER BY authority_score DESC, canonical_url
                """,
                ps -> bindUuidArray(ps, 1, language, topicClusterIds),
                (rs, rowNum) -> new DerivedCatalogLink(
                        rs.getString("canonical_url"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        "portal",
                        rs.getDouble("authority_score")));
    }

    @Override
    public List<DerivedCatalogLink> findTopPortalLinks(String language, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT canonical_url, title, summary, authority_score
                FROM ingestion.mv_portal_link
                WHERE language = ?
                ORDER BY authority_score DESC, canonical_url
                LIMIT ?
                """,
                (rs, rowNum) -> new DerivedCatalogLink(
                        rs.getString("canonical_url"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        "portal",
                        rs.getDouble("authority_score")),
                language,
                limit);
    }

    @Override
    public List<DerivedTopicCluster> loadClusterLabels(List<UUID> clusterIds) {
        List<DerivedTopicCluster> loaded = jdbcTemplate.query(
                """
                SELECT tc.id,
                       COALESCE(
                           (SELECT co.payload->>'labelKa'
                            FROM ingestion.curation_override co
                            WHERE co.action = 'rename_topic'
                              AND co.target = tc.id::text
                              AND (co.expires_at IS NULL OR co.expires_at > now())
                            LIMIT 1),
                           tc.label_ka) AS label_ka,
                       COALESCE(
                           (SELECT co.payload->>'labelEn'
                            FROM ingestion.curation_override co
                            WHERE co.action = 'rename_topic'
                              AND co.target = tc.id::text
                              AND (co.expires_at IS NULL OR co.expires_at > now())
                            LIMIT 1),
                           tc.label_en) AS label_en
                FROM ingestion.topic_cluster tc
                WHERE tc.id = ANY(?)
                """,
                ps -> bindUuidArray(ps, 1, clusterIds),
                (rs, rowNum) ->
                        new DerivedTopicCluster(
                                rs.getObject("id", UUID.class),
                                rs.getString("label_ka"),
                                rs.getString("label_en")));
        Map<UUID, DerivedTopicCluster> byId = new LinkedHashMap<>();
        for (DerivedTopicCluster cluster : loaded) {
            byId.putIfAbsent(cluster.id(), cluster);
        }
        return clusterIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<DerivedCatalogLink> findSpecificLinks(List<UUID> topicClusterIds, String language, int maxRank) {
        if (topicClusterIds == null || topicClusterIds.isEmpty() || maxRank <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT canonical_url, title, summary, page_kind, authority_score
                FROM ingestion.mv_specific_link
                WHERE language = ?
                  AND topic_cluster_id = ANY(?)
                  AND rank_in_kind <= ?
                ORDER BY authority_score DESC, rank_in_kind, canonical_url
                """,
                ps -> {
                    ps.setString(1, language);
                    bindUuidArray(ps, 2, topicClusterIds);
                    ps.setInt(3, maxRank);
                },
                (rs, rowNum) -> new DerivedCatalogLink(
                        rs.getString("canonical_url"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("page_kind"),
                        rs.getDouble("authority_score")));
    }

    private static void bindUuidArray(java.sql.PreparedStatement ps, int index, String language, List<UUID> ids)
            throws java.sql.SQLException {
        ps.setString(index, language);
        bindUuidArray(ps, index + 1, ids);
    }

    private static void bindUuidArray(java.sql.PreparedStatement ps, int index, List<UUID> ids)
            throws java.sql.SQLException {
        Connection connection = ps.getConnection();
        UUID[] values = ids.toArray(UUID[]::new);
        Array array = connection.createArrayOf("uuid", values);
        ps.setArray(index, array);
    }
}
