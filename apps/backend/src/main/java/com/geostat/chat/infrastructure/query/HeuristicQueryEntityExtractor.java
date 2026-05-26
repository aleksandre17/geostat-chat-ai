package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.QueryEntityExtractor;
import com.geostat.platform.enrichment.Entity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** RAG-U07d — fast regex/keyword entity hints (years, common indicators). */
@Component
public class HeuristicQueryEntityExtractor implements QueryEntityExtractor {

    private static final Pattern YEAR = Pattern.compile("\\b(19[9]\\d|20[0-3]\\d)\\b");
    private static final Pattern INDICATOR =
            Pattern.compile("\\b(gdp|cpi|inflation|unemployment|export|import|fdi|mshp|მშპ|ინფლაცია|უმუკველწვერა|ექსპორტი|იმპორტი)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Override
    public List<Entity> extract(String query, String normalized, String locale) {
        String haystack = normalized == null || normalized.isBlank() ? query : normalized;
        if (haystack == null || haystack.isBlank()) {
            return List.of();
        }
        List<Entity> entities = new ArrayList<>();
        Matcher yearMatcher = YEAR.matcher(haystack);
        while (yearMatcher.find()) {
            entities.add(new Entity("YEAR", yearMatcher.group(1), yearMatcher.group(1), 0.85));
        }
        Matcher indicatorMatcher = INDICATOR.matcher(haystack);
        while (indicatorMatcher.find()) {
            String value = indicatorMatcher.group(1);
            entities.add(new Entity("INDICATOR", value, normalizeIndicator(value), 0.75));
        }
        return deduplicate(entities);
    }

    private static String normalizeIndicator(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "mshp", "მშპ" -> "GDP";
            case "cpi", "ინფლაცია" -> "CPI";
            case "უმუკველწვერა" -> "UNEMPLOYMENT";
            case "ექსპორტი" -> "EXPORT";
            case "იმპორტი" -> "IMPORT";
            default -> value.toUpperCase(Locale.ROOT);
        };
    }

    private static List<Entity> deduplicate(List<Entity> entities) {
        Map<String, Entity> deduped = new LinkedHashMap<>();
        for (Entity entity : entities) {
            String key = entity.type() + "|" + entity.normalizedForm().toLowerCase(Locale.ROOT);
            deduped.putIfAbsent(key, entity);
        }
        return List.copyOf(deduped.values());
    }
}
