package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import com.geostat.chat.domain.catalog.TopicRule;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects up to 3 topics from a user query.
 *
 * Step 1: Rule engine — processes all TopicRule entries sorted by priority.
 * Step 2: AI fallback — only when Step 1 returns [GENERAL], with recent conversation context.
 */
@Component
public class TopicDetector {

    private static final Logger log = LoggerFactory.getLogger(TopicDetector.class);
    private static final int MAX_TOPICS = 3;
    private static final int MAX_CONTEXT_USER_TURNS = 2;

    private final ChatClient chatClient;
    private final TopicCatalog topicCatalog;
    private final PromptCatalog promptCatalog;
    private final AiChatOptionsFactory chatOptionsFactory;

    private record RuleEntry(Topic topic, TopicRule rule) {}

    private final List<RuleEntry> sortedRules;

    public TopicDetector(
            ChatClient chatClient,
            TopicCatalog topicCatalog,
            PromptCatalog promptCatalog,
            AiChatOptionsFactory chatOptionsFactory) {
        this.chatClient = chatClient;
        this.topicCatalog = topicCatalog;
        this.promptCatalog = promptCatalog;
        this.chatOptionsFactory = chatOptionsFactory;
        this.sortedRules = buildSortedRules();
    }

    private List<RuleEntry> buildSortedRules() {
        List<RuleEntry> entries = new ArrayList<>();
        for (TopicDefinition def : topicCatalog.all()) {
            for (TopicRule rule : def.rules()) {
                entries.add(new RuleEntry(def.topic(), rule));
            }
        }
        entries.sort(Comparator.comparingInt(e -> e.rule().priority()));
        return Collections.unmodifiableList(entries);
    }

    public List<Topic> detect(String lowerQuery, String originalQuery, Deque<Message> history) {
        List<Topic> ruleResult = detectByRules(lowerQuery);

        if (ruleResult.size() == 1 && ruleResult.get(0) == Topic.GENERAL) {
            Topic aiTopic = classifyWithAi(originalQuery, history);
            if (aiTopic != Topic.GENERAL) {
                log.info("AI fallback classification: {} for query: {}", aiTopic, originalQuery);
                return List.of(aiTopic);
            }
        }

        return ruleResult;
    }

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

    private Topic classifyWithAi(String query, Deque<Message> history) {
        String topicList = Arrays.stream(Topic.values())
                .map(Topic::name)
                .collect(Collectors.joining(", "));
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(promptCatalog.topicClassifierPrompt(topicList)));
            messages.addAll(recentUserContext(history));
            messages.add(new UserMessage(query));
            Prompt prompt = new Prompt(messages, chatOptionsFactory.classification());
            String raw = chatClient.prompt(prompt).call().content();
            if (raw == null) return Topic.GENERAL;
            String cleaned = raw.strip().toUpperCase().replaceAll("[^A-Z_]", "");
            return Topic.valueOf(cleaned);
        } catch (Exception e) {
            log.debug("AI topic classification failed: {}", e.getMessage());
            return Topic.GENERAL;
        }
    }

    private static List<Message> recentUserContext(Deque<Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Message> recent = new ArrayList<>();
        for (Message message : history) {
            if (message instanceof UserMessage um) {
                recent.add(um);
            } else if (message instanceof AssistantMessage am) {
                recent.add(am);
            }
        }
        if (recent.size() <= MAX_CONTEXT_USER_TURNS * 2) {
            return recent;
        }
        return recent.subList(recent.size() - MAX_CONTEXT_USER_TURNS * 2, recent.size());
    }
}
