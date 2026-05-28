package com.geostat.chat.infrastructure.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.chat.domain.query.QueryIntentKind;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** RAG-U07c — Gemini cheap-call intent classifier with heuristic fallback upstream. */
@Component
public class GeminiIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(GeminiIntentClassifier.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final IntentPromptTemplate promptTemplate;
    private final AiChatOptionsFactory chatOptionsFactory;
    private final HeuristicIntentClassifier heuristicFallback;

    public GeminiIntentClassifier(
            ChatClient chatClient,
            IntentPromptTemplate promptTemplate,
            AiChatOptionsFactory chatOptionsFactory,
            HeuristicIntentClassifier heuristicFallback) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
        this.chatOptionsFactory = chatOptionsFactory;
        this.heuristicFallback = heuristicFallback;
    }

    public QueryIntentKind classify(String message, String normalized, String locale) {
        try {
            String raw = chatClient
                    .prompt()
                    .options(chatOptionsFactory.intentClassification())
                    .system(promptTemplate.system())
                    .user(promptTemplate.user(message, locale))
                    .call()
                    .content();
            return parseIntentResponse(raw);
        } catch (Exception e) {
            log.debug("Gemini intent classification failed, using heuristic: {}", e.getMessage());
            return heuristicFallback.classify(message, normalized, locale);
        }
    }

    static QueryIntentKind parseIntentResponse(String raw) {
        try {
            JsonNode node = new ObjectMapper().readTree(extractJson(raw));
            String intent = textOrEmpty(node, "intent").trim().toLowerCase(Locale.ROOT);
            return QueryIntentKind.valueOf(intent.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalStateException("invalid intent JSON from model", e);
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

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
