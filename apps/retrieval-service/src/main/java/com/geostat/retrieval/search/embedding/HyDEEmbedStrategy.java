package com.geostat.retrieval.search.embedding;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.retrieval.QueryEmbeddingStrategy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hypothetical Document Embedding (HyDE) strategy.
 * Generates a hypothetical answer document and embeds it.
 * Improves semantic matching for complex queries.
 */
@Component
@ConditionalOnProperty(
        prefix = "geostat.retrieval.hyde",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class HyDEEmbedStrategy implements QueryEmbeddingStrategy {

    private static final Logger log = LoggerFactory.getLogger(HyDEEmbedStrategy.class);

    private static final String HYDE_PROMPT_KA = """
            მომხმარებელმა დასვა კითხვა: "%s"
            
            დაწერე მოკლე (2-3 წინადადება) პასუხი, რომელიც შეიძლება იყოს სტატისტიკურ პორტალზე.
            პასუხი უნდა შეიცავდეს რიცხვებს და ფაქტებს თუ შესაბამისია.
            დაწერე მხოლოდ პასუხი, არა კომენტარი.
            """;

    private static final String HYDE_PROMPT_EN = """
            User asked: "%s"
            
            Write a short (2-3 sentences) answer that might appear on a statistics portal.
            Include numbers and facts if relevant.
            Write only the answer, no commentary.
            """;

    private final ChatClient chatClient;
    private final EmbeddingPort embedding;

    @Value("${geostat.retrieval.hyde.max-tokens:256}")
    private int maxTokens;

    public HyDEEmbedStrategy(ChatClient.Builder chatClientBuilder, EmbeddingPort embedding) {
        this.chatClient = chatClientBuilder.build();
        this.embedding = embedding;
    }

    @Override
    public List<float[]> embed(String query, String locale) {
        String prompt = "ka".equalsIgnoreCase(locale) ? HYDE_PROMPT_KA : HYDE_PROMPT_EN;
        try {
            String hypotheticalDoc = chatClient.prompt()
                    .user(String.format(prompt, query))
                    .call()
                    .content();

            if (hypotheticalDoc == null || hypotheticalDoc.isBlank()) {
                log.warn("HyDE returned empty response for query: {}", query);
                return List.of();
            }

            return List.of(embedding.embed(hypotheticalDoc));
        } catch (Exception e) {
            log.warn("HyDE generation failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    @Override
    public String name() {
        return "hyde";
    }
}
