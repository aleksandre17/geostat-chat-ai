package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.QueryEntityExtractor;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import com.geostat.platform.enrichment.Entity;
import com.geostat.platform.enrichment.EntityJsonParser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** RAG-U07d — Gemini entity extraction for user queries (U01c shared parser). */
@Component
public class GeminiQueryEntityExtractor implements QueryEntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(GeminiQueryEntityExtractor.class);

    private final ChatClient chatClient;
    private final QueryEntityPromptTemplate promptTemplate;
    private final AiChatOptionsFactory chatOptionsFactory;
    private final HeuristicQueryEntityExtractor heuristicFallback;

    public GeminiQueryEntityExtractor(
            ChatClient chatClient,
            QueryEntityPromptTemplate promptTemplate,
            AiChatOptionsFactory chatOptionsFactory,
            HeuristicQueryEntityExtractor heuristicFallback) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
        this.chatOptionsFactory = chatOptionsFactory;
        this.heuristicFallback = heuristicFallback;
    }

    @Override
    public List<Entity> extract(String query, String normalized, String locale) {
        try {
            String raw = chatClient
                    .prompt()
                    .options(chatOptionsFactory.entityExtraction())
                    .system(promptTemplate.system())
                    .user(promptTemplate.user(query, locale))
                    .call()
                    .content();
            return EntityJsonParser.parseEntityResponse(raw);
        } catch (Exception e) {
            log.debug("Gemini query entity extraction failed, using heuristic: {}", e.getMessage());
            return heuristicFallback.extract(query, normalized, locale);
        }
    }
}
