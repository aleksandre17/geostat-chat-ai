package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.query.QueryEntityExtractor;
import com.geostat.platform.enrichment.Entity;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** RAG-U07d — fast regex/keyword entity hints (years, common indicators). */
@Component
public class HeuristicQueryEntityExtractor implements QueryEntityExtractor {

    private List<IndicatorEntry> indicators;

    private static final Pattern YEAR = Pattern.compile("\\b(19[5-9]\\d|20[0-3]\\d)\\b");

    private record IndicatorEntry(List<String> triggers, String normalized, String type) {}

    private record IndicatorsFile(List<IndicatorEntry> indicators) {}

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/entity-indicators.yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        IndicatorsFile file = mapper.readValue(resource.getInputStream(), IndicatorsFile.class);
        this.indicators = file.indicators() != null ? file.indicators() : List.of();
    }

    @Override
    public List<Entity> extract(String query, String normalized, String locale) {
        String haystack = normalized == null || normalized.isBlank() ? query : normalized;
        if (haystack == null || haystack.isBlank()) {
            return List.of();
        }
        List<Entity> result = new ArrayList<>();
        String lower = haystack.toLowerCase(Locale.ROOT);

        for (IndicatorEntry entry : indicators) {
            for (String trigger : entry.triggers()) {
                if (lower.contains(trigger.toLowerCase(Locale.ROOT))) {
                    result.add(new Entity(entry.type(), entry.normalized(), entry.normalized(), 0.75));
                    break;
                }
            }
        }

        Matcher yearMatcher = YEAR.matcher(haystack);
        while (yearMatcher.find()) {
            String year = yearMatcher.group();
            result.add(new Entity("YEAR", year, year, 0.85));
        }

        return deduplicate(result);
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
