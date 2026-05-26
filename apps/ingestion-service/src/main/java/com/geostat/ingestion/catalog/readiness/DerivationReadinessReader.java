package com.geostat.ingestion.catalog.readiness;

import com.geostat.ingestion.quality.persistence.CorpusPipelineMetrics;
import com.geostat.ingestion.quality.persistence.CorpusQualityReader;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class DerivationReadinessReader {

    private static final String ENRICHMENT_METRICS_SQL =
            """
            SELECT
              COUNT(d.id) FILTER (WHERE d.fetch_status = 'parsed') AS parsed_documents,
              COUNT(d.id) FILTER (
                WHERE d.fetch_status = 'parsed'
                  AND (
                    (d.summary_ka IS NOT NULL AND d.summary_ka <> '')
                    OR (d.summary_en IS NOT NULL AND d.summary_en <> '')
                  )
              ) AS with_summary,
              COUNT(d.id) FILTER (
                WHERE d.fetch_status = 'parsed' AND d.page_kind <> 'unknown'
              ) AS with_page_kind,
              COUNT(d.id) FILTER (
                WHERE d.fetch_status = 'parsed' AND d.topic_cluster_id IS NOT NULL
              ) AS with_topic_cluster
            FROM ingestion.document d
            INNER JOIN ingestion.corpus c ON c.id = d.corpus_id
            WHERE c.name = ?
            """;

    private static final String TOPIC_CLUSTER_SQL =
            """
            SELECT
              COUNT(*) FILTER (WHERE tc.approved = true) AS approved_clusters,
              COUNT(*) FILTER (WHERE tc.approved = false) AS pending_clusters
            FROM ingestion.topic_cluster tc
            INNER JOIN ingestion.corpus c ON c.id = tc.corpus_id
            WHERE c.name = ?
            """;

    private static final String PORTAL_LINK_SQL =
            """
            SELECT COUNT(*) AS portal_links
            FROM ingestion.mv_portal_link mpl
            INNER JOIN ingestion.topic_cluster tc ON tc.id = mpl.topic_cluster_id
            INNER JOIN ingestion.corpus c ON c.id = tc.corpus_id
            WHERE c.name = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CorpusQualityReader corpusQualityReader;

    public DerivationReadinessReader(JdbcTemplate jdbcTemplate, CorpusQualityReader corpusQualityReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.corpusQualityReader = corpusQualityReader;
    }

    public boolean corpusExists(String corpusName) {
        return corpusQualityReader.corpusExists(corpusName);
    }

    public EnrichmentMetrics loadEnrichmentMetrics(String corpusName) {
        return jdbcTemplate.queryForObject(
                ENRICHMENT_METRICS_SQL,
                (rs, rowNum) ->
                        new EnrichmentMetrics(
                                rs.getLong("parsed_documents"),
                                rs.getLong("with_summary"),
                                rs.getLong("with_page_kind"),
                                rs.getLong("with_topic_cluster")),
                corpusName);
    }

    public CorpusPipelineMetrics loadPipelineMetrics(String corpusName) {
        return corpusQualityReader.loadPipelineMetrics(corpusName);
    }

    public TopicClusterMetrics loadTopicClusterMetrics(String corpusName) {
        return jdbcTemplate.queryForObject(
                TOPIC_CLUSTER_SQL,
                (rs, rowNum) ->
                        new TopicClusterMetrics(
                                rs.getLong("approved_clusters"), rs.getLong("pending_clusters")),
                corpusName);
    }

    public long countPortalLinks(String corpusName) {
        Long count = jdbcTemplate.queryForObject(PORTAL_LINK_SQL, Long.class, corpusName);
        return count != null ? count : 0L;
    }

    public record EnrichmentMetrics(
            long parsedDocuments, long withSummary, long withPageKind, long withTopicCluster) {}

    public record TopicClusterMetrics(long approvedClusters, long pendingClusters) {}
}
