package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.chat.domain.query.QueryExpander;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** RAG-U07e — LLM paraphrase expansion for retrieval. */
@Component
public class GeminiQueryExpander implements QueryExpander {

    private static final Logger log = LoggerFactory.getLogger(GeminiQueryExpander.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final QueryExpandPromptTemplate promptTemplate;
    private final AiChatOptionsFactory chatOptionsFactory;

    public GeminiQueryExpander(
            ChatClient chatClient, QueryExpandPromptTemplate promptTemplate, AiChatOptionsFactory chatOptionsFactory) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
        this.chatOptionsFactory = chatOptionsFactory;
    }

    @Override
    public List<String> expand(String normalized, String locale) {
        if (normalized == null || normalized.isBlank()) {
            return List.of();
        }
        try {
            String raw = chatClient
                    .prompt()
                    .options(chatOptionsFactory.queryExpansion())
                    .system(promptTemplate.system())
                    .user(promptTemplate.user(normalized, locale))
                    .call()
                    .content();
            return parseParaphrases(raw);
        } catch (Exception e) {
            log.debug("Gemini query expansion failed: {}", e.getMessage());
            return List.of();
        }
    }

    static List<String> parseParaphrases(String raw) {
        try {
            JsonNode root = new ObjectMapper().readTree(extractJson(raw));
            JsonNode node = root.get("paraphrases");
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> phrases = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    String phrase = item.asText().strip();
                    if (!phrase.isBlank()) {
                        phrases.add(phrase);
                    }
                }
            }
            return List.copyOf(phrases);
        } catch (Exception e) {
            throw new IllegalStateException("invalid paraphrases JSON from model", e);
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        Matcher matcher = JSON_BLOCK.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return raw.trim();
    }
}
