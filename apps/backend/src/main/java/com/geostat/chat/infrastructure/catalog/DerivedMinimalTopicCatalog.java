package com.geostat.chat.infrastructure.catalog;

import com.geostat.chat.domain.catalog.LinkInfo;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.catalog.TopicRule;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Minimal {@link TopicCatalog} when {@code geostat.chat.catalog.source=derived}.
 * Link routing uses {@link com.geostat.chat.domain.catalog.DerivedCatalogReader}; this bean loads
 * topic detection rules from approved {@code ingestion.topic_cluster} rows at startup.
 */
@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class DerivedMinimalTopicCatalog implements TopicCatalog {

    private static final Logger log = LoggerFactory.getLogger(DerivedMinimalTopicCatalog.class);
    private static final LinkInfo EMPTY_LINK = new LinkInfo("", "", "");
    private static final int RULE_PRIORITY = 20;

    private final PresentationStyleCatalog presentationStyles;
    private final JdbcTemplate jdbcTemplate;
    private Map<Topic, TopicDefinition> definitions = Map.of();

    public DerivedMinimalTopicCatalog(PresentationStyleCatalog presentationStyles, JdbcTemplate catalogJdbcTemplate) {
        this.presentationStyles = presentationStyles;
        this.jdbcTemplate = catalogJdbcTemplate;
        this.definitions = loadDefinitionsFromDatabase();
    }

    private Map<Topic, TopicDefinition> loadDefinitionsFromDatabase() {
        try {
            Map<Topic, TopicDefinition> loaded = buildDefinitions(loadClusterRows());
            log.info("Loaded {} topic definitions from ingestion.topic_cluster", loaded.size());
            return loaded;
        } catch (DataAccessException e) {
            log.warn("Failed to load topic_cluster rules; topic detection falls back to AI: {}", e.getMessage());
            return Map.of();
        }
    }

    DerivedMinimalTopicCatalog(PresentationStyleCatalog presentationStyles, Map<Topic, TopicDefinition> definitions) {
        this.presentationStyles = presentationStyles;
        this.jdbcTemplate = null;
        this.definitions = Map.copyOf(definitions);
    }

    @Override
    public TopicDefinition get(Topic topic) {
        TopicDefinition loaded = definitions.get(topic);
        if (loaded != null) {
            return loaded;
        }
        var style = presentationStyles.linkTypeStyle(topic == Topic.GENERAL ? "general" : topic.name().toLowerCase());
        TopicDefinition.TopicStyle topicStyle =
                new TopicDefinition.TopicStyle(style.icon(), style.bgColor(), style.lightBg());
        return TopicDefinition.of(topic, List.of(), List.of(), null, null, null, topicStyle, 0);
    }

    @Override
    public Collection<TopicDefinition> all() {
        if (definitions.isEmpty()) {
            return List.of(get(Topic.GENERAL));
        }
        return List.copyOf(definitions.values());
    }

    @Override
    public List<LinkInfo> allPortals() {
        return List.of();
    }

    @Override
    public List<String> sectoralKeywords() {
        return List.of();
    }

    @Override
    public LinkInfo sectoralAccounts() {
        return EMPTY_LINK;
    }

    @Override
    public Set<Topic> newsRelevantTopics() {
        return EnumSet.noneOf(Topic.class);
    }

    @Override
    public List<String> latestKeywords() {
        return List.of();
    }

    @Override
    public List<LinkInfo> matchSpecificLinks(String query) {
        return List.of();
    }

    @Override
    public LinkInfo categoryNews(Topic topic, boolean isGeorgian) {
        return EMPTY_LINK;
    }

    private List<ClusterRow> loadClusterRows() {
        return jdbcTemplate.query(
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
                           tc.label_en) AS label_en,
                       tc.keywords
                FROM ingestion.topic_cluster tc
                WHERE tc.approved = true
                ORDER BY tc.id
                """,
                (rs, rowNum) -> {
                    Array keywordArray = rs.getArray("keywords");
                    String[] keywords =
                            keywordArray == null ? new String[0] : (String[]) keywordArray.getArray();
                    return new ClusterRow(rs.getString("label_ka"), rs.getString("label_en"), keywords);
                });
    }

    private Map<Topic, TopicDefinition> buildDefinitions(List<ClusterRow> rows) {
        Map<Topic, List<TopicRule>> rulesByTopic = new LinkedHashMap<>();
        for (ClusterRow row : rows) {
            List<String> keywords = normalizeKeywords(row.labelKa(), row.labelEn(), row.keywords());
            if (keywords.isEmpty()) {
                continue;
            }
            Topic topic = resolveTopic(row.labelKa(), row.labelEn(), keywords).orElse(Topic.GENERAL);
            if (topic == Topic.GENERAL) {
                continue;
            }
            rulesByTopic
                    .computeIfAbsent(topic, ignored -> new ArrayList<>())
                    .add(TopicRule.simple(RULE_PRIORITY, keywords));
        }

        Map<Topic, TopicDefinition> built = new LinkedHashMap<>();
        rulesByTopic.forEach((topic, rules) -> built.put(topic, definitionFor(topic, rules)));
        return Map.copyOf(built);
    }

    private TopicDefinition definitionFor(Topic topic, List<TopicRule> rules) {
        var style = presentationStyles.linkTypeStyle(topic.name().toLowerCase());
        TopicDefinition.TopicStyle topicStyle =
                new TopicDefinition.TopicStyle(style.icon(), style.bgColor(), style.lightBg());
        return TopicDefinition.of(topic, List.copyOf(rules), List.of(), null, null, null, topicStyle, 0);
    }

    private static List<String> normalizeKeywords(String labelKa, String labelEn, String[] keywords) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        addKeyword(normalized, labelKa);
        addKeyword(normalized, labelEn);
        if (keywords != null) {
            for (String keyword : keywords) {
                addKeyword(normalized, keyword);
            }
        }
        return List.copyOf(normalized);
    }

    private static void addKeyword(Set<String> keywords, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        keywords.add(value.strip().toLowerCase(Locale.ROOT));
    }

    private static java.util.Optional<Topic> resolveTopic(String labelKa, String labelEn, List<String> keywords) {
        java.util.Optional<Topic> fromLabel = topicFromLabel(labelEn);
        if (fromLabel.isPresent()) {
            return fromLabel;
        }
        fromLabel = topicFromLabel(labelKa);
        if (fromLabel.isPresent()) {
            return fromLabel;
        }
        return bestTopicByKeywordOverlap(keywords);
    }

    private static java.util.Optional<Topic> topicFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return java.util.Optional.empty();
        }
        String candidate = label.strip()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replaceAll("[^A-Z_]", "");
        if (candidate.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            Topic topic = Topic.valueOf(candidate);
            return topic == Topic.GENERAL ? java.util.Optional.empty() : java.util.Optional.of(topic);
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<Topic> bestTopicByKeywordOverlap(List<String> keywords) {
        Topic best = null;
        int bestScore = 0;
        for (Topic topic : Topic.values()) {
            if (topic == Topic.GENERAL) {
                continue;
            }
            int score = overlapScore(topic, keywords);
            if (score > bestScore) {
                bestScore = score;
                best = topic;
            }
        }
        return bestScore > 0 ? java.util.Optional.of(best) : java.util.Optional.empty();
    }

    private static int overlapScore(Topic topic, List<String> keywords) {
        String enumName = topic.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] enumTokens = topic.name().toLowerCase(Locale.ROOT).split("_");
        int score = 0;
        for (String keyword : keywords) {
            if (keyword.contains(enumName) || enumName.contains(keyword)) {
                score += 3;
            }
            for (String token : enumTokens) {
                if (token.length() >= 4 && keyword.contains(token)) {
                    score += 2;
                }
            }
        }
        return score;
    }

    private record ClusterRow(String labelKa, String labelEn, String[] keywords) {}
}
