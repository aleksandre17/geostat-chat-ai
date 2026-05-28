package com.geostat.ingestion.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Extracts structured data from {@code <script type="application/ld+json">} elements.
 * Provides: publishedAt, navBreadcrumb, description, schemaType (@type).
 */
@Component
public class JsonLdExtractor {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record JsonLdResult(
            @Nullable String publishedAt,
            @Nullable String navBreadcrumb,
            @Nullable String description,
            @Nullable String schemaType) {

        public boolean hasAnyData() {
            return publishedAt != null
                    || navBreadcrumb != null
                    || description != null
                    || schemaType != null;
        }
    }

    public JsonLdResult extract(Document html) {
        Elements scripts = html.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data().strip();
            if (json.isEmpty()) {
                continue;
            }
            try {
                JsonNode root = MAPPER.readTree(json);
                if (root.has("@graph") && root.get("@graph").isArray()) {
                    for (JsonNode node : root.get("@graph")) {
                        JsonLdResult r = extractFromNode(node);
                        if (r.hasAnyData()) {
                            return r;
                        }
                    }
                }
                JsonLdResult r = extractFromNode(root);
                if (r.hasAnyData()) {
                    return r;
                }
            } catch (Exception ignored) {
                // malformed JSON-LD — skip silently
            }
        }
        return new JsonLdResult(null, null, null, null);
    }

    private JsonLdResult extractFromNode(JsonNode node) {
        String publishedAt = textOrNull(node, "datePublished");
        String description = textOrNull(node, "description");
        String schemaType = textOrNull(node, "@type");
        String breadcrumb = extractBreadcrumb(node);
        return new JsonLdResult(publishedAt, breadcrumb, description, schemaType);
    }

    private String extractBreadcrumb(JsonNode node) {
        JsonNode bc = node.get("breadcrumb");
        if (bc == null) {
            bc = node;
        }
        JsonNode items = bc.get("itemListElement");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode item : items) {
            String name = textOrNull(item, "name");
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names.isEmpty() ? null : String.join(" > ", names);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && n.isTextual() && !n.asText().isBlank()) ? n.asText() : null;
    }
}
