package com.geostat.chat.application.catalog;

import com.geostat.chat.domain.catalog.ClusterMatchPort;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.DerivedTopicCluster;
import com.geostat.chat.domain.catalog.ScoredCluster;
import com.geostat.chat.domain.query.AnalyzedQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Application-layer orchestrator for multi-signal cluster scoring.
 *
 * Signals (configurable weights, sum to 1.0):
 *   A — keyword match (YAKE + Gemini, TF-IDF weighted from mv_topic_keywords)
 *   B — cluster label match
 *   C — entity-based match (inflection-safe via normalized entity forms)
 *   D — terminology expansion match (synonym coverage)
 *
 * A cluster must exceed {@code minScore} to be included. This prevents noise
 * from weak partial matches. Diversity filter prevents one topic dominating a mixed query.
 */
@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class ScoredClusterMatcher {

    private final ClusterMatchPort clusterMatchPort;
    private final DerivedCatalogReader catalogReader;
    private final double minScore;
    private final int    maxDiversityPerPrefix;

    public ScoredClusterMatcher(
            ClusterMatchPort clusterMatchPort,
            DerivedCatalogReader catalogReader,
            @Value("${geostat.chat.cluster.min-score:0.10}") double minScore,
            @Value("${geostat.chat.cluster.max-diversity-per-prefix:2}") int maxDiversityPerPrefix) {
        this.clusterMatchPort      = clusterMatchPort;
        this.catalogReader         = catalogReader;
        this.minScore              = minScore;
        this.maxDiversityPerPrefix = maxDiversityPerPrefix;
    }

    public DerivedClusterMatch match(AnalyzedQuery query, String language, int limit) {
        List<ScoredCluster> scored = clusterMatchPort.match(query, language, limit * 2);

        List<ScoredCluster> confident = scored.stream()
                .filter(c -> c.score() >= minScore)
                .toList();

        List<DerivedTopicCluster> diverse = applyDiversityAndLoadLabels(confident, limit);

        return new DerivedClusterMatch(diverse);
    }

    private List<DerivedTopicCluster> applyDiversityAndLoadLabels(
            List<ScoredCluster> scored, int limit) {

        Map<String, Integer> labelGroupCount = new LinkedHashMap<>();
        List<UUID> selectedIds = new ArrayList<>();

        for (ScoredCluster cluster : scored) {
            if (selectedIds.size() >= limit) break;
            String groupKey = labelGroupKey(cluster.labelKa(), cluster.labelEn());
            int count = labelGroupCount.getOrDefault(groupKey, 0);
            if (count < maxDiversityPerPrefix) {
                selectedIds.add(cluster.clusterId());
                labelGroupCount.put(groupKey, count + 1);
            }
        }

        if (selectedIds.isEmpty()) return List.of();
        return catalogReader.loadClusterLabels(selectedIds);
    }

    private static String labelGroupKey(String labelKa, String labelEn) {
        String label = (labelKa != null && !labelKa.isBlank()) ? labelKa : labelEn;
        if (label == null || label.isBlank()) return "";
        String[] words = label.toLowerCase().split("\\s+");
        return words.length >= 2 ? words[0] + " " + words[1] : words[0];
    }
}
