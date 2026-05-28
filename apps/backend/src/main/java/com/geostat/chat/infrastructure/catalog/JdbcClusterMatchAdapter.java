package com.geostat.chat.infrastructure.catalog;

import com.geostat.chat.domain.catalog.ClusterMatchPort;
import com.geostat.chat.domain.catalog.DerivedTopicCluster;
import com.geostat.chat.domain.catalog.ScoredCluster;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.platform.enrichment.Entity;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class JdbcClusterMatchAdapter implements ClusterMatchPort {

    private final JdbcTemplate jdbcTemplate;

    private final double wKeyword;
    private final double wLabel;
    private final double wEntity;
    private final double wExpansion;
    private final int    maxRank;
    private final double scoreSaturation;
    private final int    candidateMultiplier;

    public JdbcClusterMatchAdapter(
            JdbcTemplate catalogJdbcTemplate,
            @Value("${geostat.chat.cluster.weight.keyword:0.35}")      double wKeyword,
            @Value("${geostat.chat.cluster.weight.label:0.25}")        double wLabel,
            @Value("${geostat.chat.cluster.weight.entity:0.30}")       double wEntity,
            @Value("${geostat.chat.cluster.weight.expansion:0.10}")    double wExpansion,
            @Value("${geostat.chat.cluster.mv-keyword-rank:30}")       int    maxRank,
            @Value("${geostat.chat.cluster.score.saturation:5.0}")     double scoreSaturation,
            @Value("${geostat.chat.cluster.candidate-multiplier:2}")   int    candidateMultiplier) {
        this.jdbcTemplate         = catalogJdbcTemplate;
        this.wKeyword             = wKeyword;
        this.wLabel               = wLabel;
        this.wEntity              = wEntity;
        this.wExpansion           = wExpansion;
        this.maxRank              = maxRank;
        this.scoreSaturation      = scoreSaturation;
        this.candidateMultiplier  = candidateMultiplier;
    }

    @Override
    public List<ScoredCluster> match(AnalyzedQuery query, String language, int limit) {
        String normalized = query.normalized() == null ? "" : query.normalized().strip().toLowerCase();
        if (normalized.length() < 3) return List.of();

        List<String> entityForms   = extractEntityForms(query);
        List<String> expansionForms = query.expansions() == null ? List.of() : query.expansions();

        int candidateLimit = limit * candidateMultiplier;

        // Step 1: SQL signals A + B
        Map<UUID, double[]> signalScores = querySignalsAB(normalized, language, maxRank, candidateLimit);

        // Step 2: batched Java signals C + D
        applyBatchedFormSignal(signalScores, entityForms,   language, 2, candidateLimit);
        applyBatchedFormSignal(signalScores, expansionForms, language, 3, candidateLimit);

        // Step 3: score fusion → candidate list
        List<Map.Entry<UUID, double[]>> fused = signalScores.entrySet().stream()
                .filter(e -> computeScore(e.getValue()) > 0)
                .sorted(Comparator.comparingDouble(e -> -computeScore(e.getValue())))
                .limit((long) limit * candidateMultiplier)
                .toList();

        if (fused.isEmpty()) return List.of();

        // Step 4: load labels for top candidates (single round-trip)
        List<UUID> topIds = fused.stream().map(Map.Entry::getKey).toList();
        Map<UUID, DerivedTopicCluster> labelMap = loadLabelsAsMap(topIds);

        // Step 5: build ScoredCluster with labels
        return fused.stream()
                .map(e -> {
                    DerivedTopicCluster label = labelMap.get(e.getKey());
                    return new ScoredCluster(
                            e.getKey(),
                            computeScore(e.getValue()),
                            label != null ? label.labelKa() : "",
                            label != null ? label.labelEn() : "",
                            buildReason(e.getValue()));
                })
                .limit(limit)
                .toList();
    }

    private Map<UUID, double[]> querySignalsAB(String query, String language, int maxRank, int limit) {
        Map<UUID, double[]> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
            SELECT cluster_id, SUM(keyword_score) AS keyword_score, SUM(label_score) AS label_score
            FROM (
                SELECT tc.id AS cluster_id,
                       SUM(tk.tfidf_score) AS keyword_score,
                       0.0 AS label_score
                FROM ingestion.topic_cluster tc
                JOIN ingestion.mv_topic_keywords tk
                  ON tk.topic_cluster_id = tc.id AND tk.language = ?
                WHERE tc.approved = true
                  AND tk.rank <= ?
                  AND position(tk.keyword IN ?) > 0
                GROUP BY tc.id

                UNION ALL

                SELECT tc.id AS cluster_id,
                       0.0   AS keyword_score,
                       1.0   AS label_score
                FROM ingestion.topic_cluster tc
                WHERE tc.approved = true
                  AND (position(lower(tc.label_ka) IN ?) > 0
                    OR position(lower(tc.label_en) IN ?) > 0)
            ) signals
            GROUP BY cluster_id
            ORDER BY SUM(keyword_score) + SUM(label_score) DESC
            LIMIT ?
            """,
            rs -> {
                UUID id = rs.getObject("cluster_id", UUID.class);
                double[] scores = result.computeIfAbsent(id, k -> new double[4]);
                scores[0] += rs.getDouble("keyword_score");
                scores[1] += rs.getDouble("label_score");
            },
            language, maxRank, query, query, query, limit);
        return result;
    }

    private void applyBatchedFormSignal(Map<UUID, double[]> scores, List<String> forms,
                                         String language, int signalIndex, int limitCandidates) {
        List<String> valid = forms.stream()
                .filter(f -> f != null && f.length() >= 3)
                .map(String::toLowerCase)
                .distinct()
                .toList();
        if (valid.isEmpty()) return;

        jdbcTemplate.query(
            """
            SELECT tc.id AS cluster_id, SUM(tk.tfidf_score) AS score
            FROM ingestion.topic_cluster tc
            JOIN ingestion.mv_topic_keywords tk
              ON tk.topic_cluster_id = tc.id AND tk.language = ?
            CROSS JOIN unnest(CAST(? AS text[])) AS t(form)
            WHERE tc.approved = true
              AND tk.rank <= ?
              AND position(t.form IN tk.keyword) > 0
            GROUP BY tc.id
            ORDER BY SUM(tk.tfidf_score) DESC
            LIMIT ?
            """,
            ps -> {
                ps.setString(1, language);
                Array formsArray = ps.getConnection().createArrayOf("text", valid.toArray());
                ps.setArray(2, formsArray);
                ps.setInt(3, maxRank);
                ps.setInt(4, limitCandidates);
            },
            rs -> {
                UUID id = rs.getObject("cluster_id", UUID.class);
                double[] s = scores.computeIfAbsent(id, k -> new double[4]);
                s[signalIndex] += rs.getDouble("score");
            });
    }

    private Map<UUID, DerivedTopicCluster> loadLabelsAsMap(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, DerivedTopicCluster> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT id, label_ka, label_en FROM ingestion.topic_cluster WHERE id = ANY(?)",
            ps -> {
                UUID[] arr = ids.toArray(UUID[]::new);
                ps.setArray(1, ps.getConnection().createArrayOf("uuid", arr));
            },
            rs -> {
                UUID id = rs.getObject("id", UUID.class);
                result.put(id, new DerivedTopicCluster(id,
                        rs.getString("label_ka"), rs.getString("label_en")));
            });
        return result;
    }

    private double computeScore(double[] signals) {
        return wKeyword  * normalize(signals[0])
             + wLabel    * normalize(signals[1])
             + wEntity   * normalize(signals[2])
             + wExpansion * normalize(signals[3]);
    }

    private double normalize(double raw) {
        if (raw <= 0) return 0.0;
        return Math.min(1.0, raw / scoreSaturation);
    }

    private static String buildReason(double[] signals) {
        StringBuilder sb = new StringBuilder();
        if (signals[0] > 0) sb.append("kw:").append(String.format("%.2f", signals[0])).append(' ');
        if (signals[1] > 0) sb.append("label ");
        if (signals[2] > 0) sb.append("entity:").append(String.format("%.2f", signals[2])).append(' ');
        if (signals[3] > 0) sb.append("expansion:").append(String.format("%.2f", signals[3]));
        return sb.toString().trim();
    }

    private static List<String> extractEntityForms(AnalyzedQuery query) {
        if (query.entities() == null || query.entities().isEmpty()) return List.of();
        List<String> forms = new ArrayList<>();
        for (Entity entity : query.entities()) {
            if (entity.normalizedForm() != null && !entity.normalizedForm().isBlank()) {
                forms.add(entity.normalizedForm().toLowerCase());
            }
            if (entity.value() != null && !entity.value().isBlank()
                    && !entity.value().equalsIgnoreCase(entity.normalizedForm())) {
                forms.add(entity.value().toLowerCase());
            }
        }
        return forms;
    }
}
