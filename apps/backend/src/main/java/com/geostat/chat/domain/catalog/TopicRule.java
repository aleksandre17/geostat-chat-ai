package com.geostat.chat.domain.catalog;

import java.util.List;
import java.util.Set;

/**
 * Declarative detection rule for a topic.
 *
 * priority  — lower number runs first (compound/disambiguation rules use 10, simple rules use 20)
 * allOf     — ALL of these must be present in query (AND)
 * anyOf     — at least ONE must be present in query (OR); empty means no OR requirement
 * excludes  — topics to suppress when this rule matches (disambiguation)
 */
public record TopicRule(
        int         priority,
        List<String> allOf,
        List<String> anyOf,
        Set<Topic>  excludes
) {
    public boolean matches(String lowerQuery) {
        return allOf.stream().allMatch(lowerQuery::contains)
            && (anyOf.isEmpty() || anyOf.stream().anyMatch(lowerQuery::contains));
    }

    // Factory helpers for concise rule definitions
    public static TopicRule simple(int priority, List<String> anyOf) {
        return new TopicRule(priority, List.of(), anyOf, Set.of());
    }

    public static TopicRule compound(List<String> allOf, List<String> anyOf, Set<Topic> excludes) {
        return new TopicRule(10, allOf, anyOf, excludes);
    }
}