package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.CorpusContextFormatter;
import com.geostat.chat.domain.chat.ChatContext;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * When no catalog/RAG link cards match — uses indexed corpus passages (ingestion pipeline)
 * or asks one clarifying question.
 */
@Component
public class ClarificationService {

    private static final Logger log = LoggerFactory.getLogger(ClarificationService.class);

    private final ChatClient chatClient;
    private final AiResponseParser aiResponseParser;
    private final SmallTalkHandler smallTalkHandler;
    private final PromptCatalog promptCatalog;
    private final AiChatOptionsFactory chatOptionsFactory;
    private final AiChatProperties aiChatProperties;

    public ClarificationService(
            ChatClient chatClient,
            AiResponseParser aiResponseParser,
            SmallTalkHandler smallTalkHandler,
            PromptCatalog promptCatalog,
            AiChatOptionsFactory chatOptionsFactory,
            AiChatProperties aiChatProperties) {
        this.chatClient = chatClient;
        this.aiResponseParser = aiResponseParser;
        this.smallTalkHandler = smallTalkHandler;
        this.promptCatalog = promptCatalog;
        this.chatOptionsFactory = chatOptionsFactory;
        this.aiChatProperties = aiChatProperties;
    }

    public AiChatResult generate(ChatContext ctx, List<RetrievedChunk> corpusChunks) {
        Set<String> allowedUrls = CorpusUrlWhitelist.fromChunks(corpusChunks);
        try {
            String corpusContext = CorpusContextFormatter.formatPassages(
                    corpusChunks, ctx.isGeorgian(), aiChatProperties.maxRagContextChars());
            String systemPrompt = promptCatalog.clarificationPrompt(ctx.isGeorgian())
                    .replace("{CORPUS_CONTEXT}", corpusContext);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(HistoryBudgetTrimmer.trim(ctx.history(), aiChatProperties.maxHistoryMessages()));
            messages.add(new UserMessage(ctx.message()));
            Prompt prompt = new Prompt(messages, chatOptionsFactory.clarification());
            String raw = chatClient.prompt(prompt).call().content();
            if (raw != null && !raw.isBlank()) {
                AiChatResult parsed = aiResponseParser.parseClarification(raw, ctx.isGeorgian(), allowedUrls);
                if (parsed.intro() != null && !parsed.intro().isBlank()) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.warn("Clarification generation failed: {}", e.getMessage());
        }
        return AiChatResult.emptyIntro(smallTalkHandler.clarificationRequest(ctx.isGeorgian()));
    }
}
