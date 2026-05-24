package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.TopicStyleCatalog;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.prompt.PromptCatalog;
import org.springframework.stereotype.Component;

import com.geostat.chat.application.retrieval.CorpusContextFormatter;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds AI system prompts from YAML catalog + topic/link/RAG context (B-27).
 *
 * Uses {TOPICS}, {RESOURCES}, {YEAR}, {RAG_CONTEXT} placeholders.
 */
@Component
public class PromptBuilder {

    private final PromptCatalog promptCatalog;
    private final AiChatProperties aiChatProperties;

    public PromptBuilder(PromptCatalog promptCatalog, AiChatProperties aiChatProperties) {
        this.promptCatalog = promptCatalog;
        this.aiChatProperties = aiChatProperties;
    }

    public String build(List<Topic> topics, List<LinkCard> links, boolean isGeorgian) {
        return build(topics, links, isGeorgian, List.of());
    }

    public String build(List<Topic> topics, List<LinkCard> links, boolean isGeorgian,
                        List<RetrievedChunk> ragChunks) {
        int ragBudget = aiChatProperties.maxRagContextChars();
        List<RetrievedChunk> trimmed = PromptBudgetTrimmer.trimChunks(ragChunks, ragBudget);
        String prompt = assemble(topics, links, isGeorgian, trimmed);
        int adjusted = PromptBudgetTrimmer.effectiveRagBudget(
                ragBudget, prompt.length(), aiChatProperties.maxSystemPromptChars());
        if (adjusted < ragBudget) {
            trimmed = PromptBudgetTrimmer.trimChunks(ragChunks, adjusted);
            prompt = assemble(topics, links, isGeorgian, trimmed);
        }
        return prompt;
    }

    private String assemble(
            List<Topic> topics, List<LinkCard> links, boolean isGeorgian, List<RetrievedChunk> ragChunks) {
        String resourcesContext = links.stream()
                .map(l -> {
                    String title = isGeorgian ? l.titleKa() : l.titleEn();
                    String typeLabel = TopicStyleCatalog.getLinkTypeLabel(l.type(), isGeorgian);
                    return "- [" + typeLabel + "] " + title + " | " + l.url();
                })
                .collect(Collectors.joining("\n"));
        String topicNames = topics.stream().map(Topic::name).collect(Collectors.joining(", "));
        return promptCatalog.mainPrompt(isGeorgian)
                .replace("{TOPICS}", topicNames)
                .replace("{RESOURCES}", resourcesContext)
                .replace("{YEAR}", String.valueOf(LocalDate.now().getYear()))
                .replace("{RAG_CONTEXT}", CorpusContextFormatter.formatPassages(
                        ragChunks, isGeorgian, aiChatProperties.maxRagContextChars()));
    }
}
