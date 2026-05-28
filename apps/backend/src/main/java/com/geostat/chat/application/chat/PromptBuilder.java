package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.CorpusContextFormatter;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds AI system prompts from YAML catalog + topic/link/RAG context (B-27).
 *
 * Uses {TOPICS}, {RESOURCES}, {YEAR}, {RAG_CONTEXT} placeholders.
 */
@Component
public class PromptBuilder {

    private final PromptCatalog promptCatalog;
    private final AiChatProperties aiChatProperties;
    private final PresentationStyleCatalog presentationStyles;
    private final int minRagChars;
    private final int budgetSafetyMargin;

    public PromptBuilder(
            PromptCatalog promptCatalog,
            AiChatProperties aiChatProperties,
            PresentationStyleCatalog presentationStyles,
            @Value("${geostat.chat.budget.min-rag-chars:1500}") int minRagChars,
            @Value("${geostat.chat.budget.safety-margin:500}") int budgetSafetyMargin) {
        this.promptCatalog       = promptCatalog;
        this.aiChatProperties    = aiChatProperties;
        this.presentationStyles  = presentationStyles;
        this.minRagChars         = minRagChars;
        this.budgetSafetyMargin  = budgetSafetyMargin;
    }

    public String build(
            CatalogTopicLabelResolver.Labels topicLabels,
            List<LinkCard> links,
            boolean isGeorgian,
            List<RetrievedChunk> ragChunks) {
        int ragBudget = aiChatProperties.maxRagContextChars();
        List<RetrievedChunk> trimmed = PromptBudgetTrimmer.trimChunks(ragChunks, ragBudget);
        String prompt = assemble(topicLabels, links, isGeorgian, trimmed);
        int adjusted = PromptBudgetTrimmer.effectiveRagBudget(
                ragBudget,
                prompt.length(),
                aiChatProperties.maxSystemPromptChars(),
                minRagChars,
                budgetSafetyMargin);
        if (adjusted < ragBudget) {
            trimmed = PromptBudgetTrimmer.trimChunks(ragChunks, adjusted);
            prompt = assemble(topicLabels, links, isGeorgian, trimmed);
        }
        return prompt;
    }

    private String assemble(
            CatalogTopicLabelResolver.Labels topicLabels,
            List<LinkCard> links,
            boolean isGeorgian,
            List<RetrievedChunk> ragChunks) {
        String resourcesContext = links.stream()
                .map(l -> {
                    String title     = isGeorgian ? l.titleKa() : l.titleEn();
                    String typeLabel = presentationStyles.linkTypeLabel(l.type(), isGeorgian);
                    return "- [" + typeLabel + "] " + title + " | " + l.url();
                })
                .collect(Collectors.joining("\n"));
        String topicNames = String.join(", ", topicLabels.all());
        return promptCatalog.mainPrompt(isGeorgian)
                .replace("{TOPICS}", topicNames)
                .replace("{RESOURCES}", resourcesContext)
                .replace("{YEAR}", String.valueOf(LocalDate.now().getYear()))
                .replace("{RAG_CONTEXT}", CorpusContextFormatter.formatPassages(
                        ragChunks, isGeorgian, aiChatProperties.maxRagContextChars()));
    }
}
