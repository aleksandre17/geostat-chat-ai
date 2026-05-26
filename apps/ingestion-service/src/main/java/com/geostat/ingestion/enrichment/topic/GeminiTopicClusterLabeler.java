package com.geostat.ingestion.enrichment.topic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.ingestion.enrichment.prompt.TopicLabelPromptTemplate;
import com.geostat.ingestion.enrichment.vectors.DocumentSummaryText;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class GeminiTopicClusterLabeler {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final TopicLabelPromptTemplate promptTemplate;

    public GeminiTopicClusterLabeler(ChatClient enrichmentChatClient, TopicLabelPromptTemplate promptTemplate) {
        this.chatClient = enrichmentChatClient;
        this.promptTemplate = promptTemplate;
    }

    public TopicClusterLabels labelCluster(List<DocumentEntity> sampleDocuments) {
        String samples = formatSamples(sampleDocuments);
        String raw = chatClient
                .prompt()
                .system(promptTemplate.system())
                .user(promptTemplate.user(samples))
                .call()
                .content();
        return parseLabelResponse(raw);
    }

    static String formatSamples(List<DocumentEntity> documents) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (DocumentEntity document : documents) {
            if (document == null) {
                continue;
            }
            builder.append(index++)
                    .append(". ")
                    .append(nullToEmpty(document.getTitle()))
                    .append(" — ")
                    .append(DocumentSummaryText.pickForEmbedding(document))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    static TopicClusterLabels parseLabelResponse(String raw) {
        try {
            JsonNode node = new ObjectMapper().readTree(extractJson(raw));
            String labelKa = textOrEmpty(node, "label_ka").trim();
            String labelEn = textOrEmpty(node, "label_en").trim();
            String centroidSummary = textOrEmpty(node, "centroid_summary").trim();
            List<String> keywords = parseKeywords(node.get("keywords"));
            if (labelKa.isBlank() && labelEn.isBlank()) {
                throw new IllegalStateException("empty topic labels in model response");
            }
            if (labelKa.isBlank()) {
                labelKa = labelEn;
            }
            if (labelEn.isBlank()) {
                labelEn = labelKa;
            }
            return new TopicClusterLabels(labelKa, labelEn, centroidSummary, keywords);
        } catch (Exception e) {
            throw new IllegalStateException("invalid topic label JSON from model", e);
        }
    }

    private static List<String> parseKeywords(JsonNode keywordsNode) {
        List<String> keywords = new ArrayList<>();
        if (keywordsNode == null || !keywordsNode.isArray()) {
            return keywords;
        }
        for (JsonNode item : keywordsNode) {
            if (item != null && item.isTextual()) {
                String keyword = item.asText().trim().toLowerCase();
                if (!keyword.isBlank()) {
                    keywords.add(keyword);
                }
            }
        }
        return keywords;
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
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
        return value != null && !value.isNull() ? value.asText() : "";
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
