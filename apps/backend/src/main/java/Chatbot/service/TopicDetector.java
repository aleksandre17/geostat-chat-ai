package Chatbot.service;

import Chatbot.catalog.TopicRegistry;
import Chatbot.model.Topic;
import Chatbot.model.TopicDefinition;
import Chatbot.model.TopicRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects up to 3 topics from a user query.
 *
 * Step 1: Rule engine — processes all TopicRule entries sorted by priority.
 *         Compound rules (priority 10) run before simple rules (priority 20)
 *         and can exclude conflicting topics (disambiguation).
 * Step 2: AI fallback — only when Step 1 returns [GENERAL].
 *         Uses temperature=0.0 for deterministic classification.
 */
@Component
public class TopicDetector {

    private static final Logger log = LoggerFactory.getLogger(TopicDetector.class);
    private static final int MAX_TOPICS = 3;

    private final ChatClient chatClient;

    // Pre-sorted rule index: (topic, rule) pairs ordered by priority ascending.
    // Built once at startup from the registry.
    private record RuleEntry(Topic topic, TopicRule rule) {}
    private final List<RuleEntry> sortedRules;

    public TopicDetector(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.sortedRules = buildSortedRules();
    }

    private List<RuleEntry> buildSortedRules() {
        List<RuleEntry> entries = new ArrayList<>();
        for (TopicDefinition def : TopicRegistry.all()) {
            for (TopicRule rule : def.rules()) {
                entries.add(new RuleEntry(def.topic(), rule));
            }
        }
        entries.sort(Comparator.comparingInt(e -> e.rule().priority()));
        return Collections.unmodifiableList(entries);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * @param lowerQuery   query already lowercased (for rule matching)
     * @param originalQuery original casing (for AI fallback prompt)
     * @return non-empty list; first element is the primary topic
     */
    public List<Topic> detect(String lowerQuery, String originalQuery) {
        List<Topic> ruleResult = detectByRules(lowerQuery);

        if (ruleResult.size() == 1 && ruleResult.get(0) == Topic.GENERAL) {
            Topic aiTopic = classifyWithAi(originalQuery);
            if (aiTopic != Topic.GENERAL) {
                log.info("AI fallback classification: {} for query: {}", aiTopic, originalQuery);
                return List.of(aiTopic);
            }
        }

        return ruleResult;
    }

    // ─── Rule engine ─────────────────────────────────────────────────────────

    private List<Topic> detectByRules(String lowerQuery) {
        LinkedHashSet<Topic> detected = new LinkedHashSet<>();
        Set<Topic> excluded = new HashSet<>();

        for (RuleEntry entry : sortedRules) {
            if (excluded.contains(entry.topic())) continue;
            if (entry.rule().matches(lowerQuery)) {
                detected.add(entry.topic());
                excluded.addAll(entry.rule().excludes());
            }
        }

        if (detected.isEmpty()) return List.of(Topic.GENERAL);

        List<Topic> result = new ArrayList<>(detected);
        return result.size() > MAX_TOPICS ? result.subList(0, MAX_TOPICS) : result;
    }

    // ─── AI fallback ─────────────────────────────────────────────────────────

    private static final String CLASSIFICATION_PROMPT = """
            You are a topic classifier for the National Statistics Office of Georgia (GeoStat).
            Given a user query, reply with EXACTLY ONE topic name from the list below — nothing else, no explanation.

            Topics: %s

            Rules:
            - Only classify if you are confident the query clearly relates to one of these GeoStat topics
            - Handle typos and transliteration (e.g. "inflacia" = PRICES, "dasaqmeba" = EMPLOYMENT, "turisti" = TOURISM)
            - When in doubt, reply: GENERAL
            - Greetings, unrelated questions, or ambiguous input → reply: GENERAL
            """;

    private Topic classifyWithAi(String query) {
        String topicList = Arrays.stream(Topic.values())
                .map(Topic::name)
                .collect(Collectors.joining(", "));
        try {
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(String.format(CLASSIFICATION_PROMPT, topicList)),
                            new UserMessage(query)),
                    ChatOptions.builder().temperature(0.0).build()
            );
            String raw = chatClient.prompt(prompt).call().content();
            if (raw == null) return Topic.GENERAL;
            String cleaned = raw.strip().toUpperCase().replaceAll("[^A-Z_]", "");
            return Topic.valueOf(cleaned);
        } catch (Exception e) {
            log.debug("AI topic classification failed: {}", e.getMessage());
            return Topic.GENERAL;
        }
    }
}