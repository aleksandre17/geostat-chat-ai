package com.geostat.ingestion.enrichment.pagekind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.ingestion.enrichment.prompt.PageKindPromptTemplate;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.PageKindClassifier;
import com.geostat.platform.enrichment.PageKindResult;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class GeminiFewShotPageKindClassifier implements PageKindClassifier {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final PageKindPromptTemplate promptTemplate;
    private final EnrichmentProperties properties;

    public GeminiFewShotPageKindClassifier(
            ChatClient enrichmentChatClient,
            PageKindPromptTemplate promptTemplate,
            EnrichmentProperties properties) {
        this.chatClient = enrichmentChatClient;
        this.promptTemplate = promptTemplate;
        this.properties = properties;
    }

    @Override
    public PageKindResult classify(DocumentContext document) {
        String modelVersion = properties.pageKindModelVersion();
        Optional<PageKindResult> heuristic = PageKindUrlHeuristic.classify(document, modelVersion);
        if (heuristic.isPresent()) {
            return heuristic.get();
        }

        String user = promptTemplate.user(
                document.title(),
                document.sectionPath(),
                document.url(),
                document.language(),
                document.contentText());
        String raw = chatClient
                .prompt()
                .system(promptTemplate.system())
                .user(user)
                .call()
                .content();
        return parsePageKindResponse(raw, modelVersion);
    }

    static PageKindResult parsePageKindResponse(String raw, String modelVersion) {
        try {
            JsonNode node = new ObjectMapper().readTree(extractJson(raw));
            String kind = textOrEmpty(node, "page_kind").trim().toLowerCase(Locale.ROOT);
            if (kind.isBlank()) {
                kind = textOrEmpty(node, "kind").trim().toLowerCase(Locale.ROOT);
            }
            if (!PageKindValues.ALLOWED.contains(kind)) {
                kind = PageKindValues.UNKNOWN;
            }
            double confidence = parseConfidence(node.get("confidence"));
            return new PageKindResult(kind, confidence, modelVersion);
        } catch (Exception e) {
            throw new IllegalStateException("invalid page_kind JSON from model", e);
        }
    }

    private static double parseConfidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.75;
        }
        double value = node.asDouble(0.75);
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("empty model response");
        }
        Matcher matcher = JSON_BLOCK.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText("") : "";
    }
}
