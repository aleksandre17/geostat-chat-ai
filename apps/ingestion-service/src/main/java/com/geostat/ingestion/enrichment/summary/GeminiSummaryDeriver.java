package com.geostat.ingestion.enrichment.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.ingestion.enrichment.prompt.SummaryPromptTemplate;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.SummaryDeriver;
import com.geostat.platform.enrichment.SummaryResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class GeminiSummaryDeriver implements SummaryDeriver {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);
    private static final int MAX_SUMMARY_CHARS = 320;

    private final ChatClient chatClient;
    private final SummaryPromptTemplate promptTemplate;
    private final EnrichmentProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiSummaryDeriver(
            ChatClient enrichmentChatClient,
            SummaryPromptTemplate promptTemplate,
            EnrichmentProperties properties,
            ObjectMapper objectMapper) {
        this.chatClient = enrichmentChatClient;
        this.promptTemplate = promptTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SummaryResult derive(DocumentContext document) {
        String user = promptTemplate.user(document.title(), document.sectionPath(), document.contentText());
        String raw = chatClient
                .prompt()
                .system(promptTemplate.system())
                .user(user)
                .call()
                .content();
        return parseSummaryResponse(raw, properties.modelVersion());
    }

    static SummaryResult parseSummaryResponse(String raw, String modelVersion) {
        try {
            JsonNode node = new ObjectMapper().readTree(extractJson(raw));
            String summaryKa = trimSummary(textOrEmpty(node, "summary_ka"));
            String summaryEn = trimSummary(textOrEmpty(node, "summary_en"));
            if (summaryKa.isBlank() && summaryEn.isBlank()) {
                throw new IllegalStateException("empty summary fields in model response");
            }
            return new SummaryResult(summaryKa, summaryEn, modelVersion);
        } catch (Exception e) {
            throw new IllegalStateException("invalid summary JSON from model", e);
        }
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

    private static String trimSummary(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_SUMMARY_CHARS ? trimmed : trimmed.substring(0, MAX_SUMMARY_CHARS);
    }
}
