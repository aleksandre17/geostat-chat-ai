package com.geostat.platform.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared entity JSON parsing for U01c document deriver and U07d query extractor. */
public final class EntityJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_OBJECT_BLOCK =
            Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);
    private static final Pattern JSON_ARRAY_BLOCK =
            Pattern.compile("```(?:json)?\\s*(\\[.*])\\s*```", Pattern.DOTALL);
    private static final Set<String> ALLOWED_TYPES =
            Set.of("INDICATOR", "YEAR", "REGION", "ORGANIZATION", "INDEX_CODE");

    private EntityJsonParser() {}

    public static List<Entity> parseEntityResponse(String raw) {
        try {
            JsonNode root = MAPPER.readTree(extractJson(raw));
            JsonNode entitiesNode = root.isArray() ? root : root.get("entities");
            if (entitiesNode == null || !entitiesNode.isArray()) {
                throw new IllegalStateException("missing entities array in model response");
            }
            List<Entity> parsed = new ArrayList<>();
            for (JsonNode item : entitiesNode) {
                Entity entity = parseEntity(item);
                if (entity != null) {
                    parsed.add(entity);
                }
            }
            return deduplicate(parsed);
        } catch (Exception e) {
            throw new IllegalStateException("invalid entities JSON from model", e);
        }
    }

    public static List<Entity> deduplicate(List<Entity> entities) {
        Map<String, Entity> deduped = new LinkedHashMap<>();
        for (Entity entity : entities) {
            String key = entity.type() + "|" + entity.normalizedForm().toLowerCase(Locale.ROOT);
            deduped.putIfAbsent(key, entity);
        }
        return new ArrayList<>(deduped.values());
    }

    private static Entity parseEntity(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String type = textOrEmpty(item, "type").trim().toUpperCase(Locale.ROOT);
        String value = textOrEmpty(item, "value").trim();
        if (type.isBlank() || value.isBlank() || !ALLOWED_TYPES.contains(type)) {
            return null;
        }
        if ("YEAR".equals(type) && !isValidYear(value)) {
            return null;
        }
        String normalizedForm = textOrEmpty(item, "normalizedForm").trim();
        if (normalizedForm.isBlank()) {
            normalizedForm = defaultNormalizedForm(type, value);
        }
        double confidence = parseConfidence(item.get("confidence"));
        return new Entity(type, value, normalizedForm, confidence);
    }

    private static boolean isValidYear(String value) {
        try {
            int year = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            return year >= 1990 && year <= 2030;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String defaultNormalizedForm(String type, String value) {
        return switch (type) {
            case "INDICATOR", "INDEX_CODE" -> value.toUpperCase(Locale.ROOT);
            case "YEAR" -> value.replaceAll("[^0-9]", "");
            default -> value;
        };
    }

    private static double parseConfidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return 1.0;
        }
        double value = node.asDouble(1.0);
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("empty model response");
        }
        String trimmed = raw.trim();
        Matcher objectMatcher = JSON_OBJECT_BLOCK.matcher(trimmed);
        if (objectMatcher.find()) {
            return objectMatcher.group(1);
        }
        Matcher arrayMatcher = JSON_ARRAY_BLOCK.matcher(trimmed);
        if (arrayMatcher.find()) {
            return arrayMatcher.group(1);
        }
        int arrayStart = trimmed.indexOf('[');
        int objectStart = trimmed.indexOf('{');
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            int arrayEnd = trimmed.lastIndexOf(']');
            if (arrayEnd > arrayStart) {
                return trimmed.substring(arrayStart, arrayEnd + 1);
            }
        }
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isNumber()) {
            return value.asText();
        }
        return value.asText("");
    }
}
