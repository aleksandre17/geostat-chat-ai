package com.geostat.retrieval.search.embedding;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.retrieval.QueryEmbeddingStrategy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Multi-query embedding strategy.
 * Generates multiple paraphrases of the query and embeds each.
 * Improves recall by covering different phrasings.
 */
@Component
@ConditionalOnProperty(
        prefix = "geostat.retrieval.multiquery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class MultiQueryEmbedStrategy implements QueryEmbeddingStrategy {

    private static final Logger log = LoggerFactory.getLogger(MultiQueryEmbedStrategy.class);

    private static final String MULTIQUERY_PROMPT_KA = """
            მომხმარებელმა დასვა კითხვა: "%s"
            
            გენერირე 3 სხვადასხვა ვარიანტი ამ კითხვის, რომელიც იგივე ინფორმაციას ეძებს.
            გამოიყენე სხვადასხვა სიტყვები და ფრაზები.
            დააბრუნე მხოლოდ 3 ვარიანტი, თითო ახალ ხაზზე. არა ნომრები, არა ტირეები.
            """;

    private static final String MULTIQUERY_PROMPT_EN = """
            User asked: "%s"
            
            Generate 3 different variations of this question that search for the same information.
            Use different words and phrases.
            Return only 3 variations, one per line. No numbers, no dashes.
            """;

    private final ChatClient chatClient;
    private final EmbeddingPort embedding;

    @Value("${geostat.retrieval.multiquery.max-variations:3}")
    private int maxVariations;

    public MultiQueryEmbedStrategy(ChatClient.Builder chatClientBuilder, EmbeddingPort embedding) {
        this.chatClient = chatClientBuilder.build();
        this.embedding = embedding;
    }

    @Override
    public List<float[]> embed(String query, String locale) {
        String prompt = "ka".equalsIgnoreCase(locale) ? MULTIQUERY_PROMPT_KA : MULTIQUERY_PROMPT_EN;
        try {
            String response = chatClient.prompt()
                    .user(String.format(prompt, query))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.warn("MultiQuery returned empty response for query: {}", query);
                return List.of();
            }

            String[] variations = response.split("\n");
            List<float[]> vectors = new ArrayList<>();

            for (String variation : variations) {
                String trimmed = variation.trim();
                if (!trimmed.isEmpty() && vectors.size() < maxVariations) {
                    vectors.add(embedding.embed(trimmed));
                }
            }

            return vectors;
        } catch (Exception e) {
            log.warn("MultiQuery generation failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    @Override
    public String name() {
        return "multiquery";
    }
}
