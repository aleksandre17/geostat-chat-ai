package com.geostat.chat.application.chat;

import com.geostat.chat.application.telemetry.ChatTelemetryService;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.chat.AiChatResult;
import com.geostat.chat.domain.chat.ChatContext;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.session.ConversationHistory;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
class ChatResponseComposer {

    private static final Logger log = LoggerFactory.getLogger(ChatResponseComposer.class);

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final AiResponseParser aiResponseParser;
    private final ResponseGroundingEnforcer responseGroundingEnforcer;
    private final ResponseSanitizer responseSanitizer;
    private final SessionTurnRecorder sessionTurnRecorder;
    private final ChatTelemetryService chatTelemetryService;
    private final PromptCatalog promptCatalog;
    private final AiChatOptionsFactory chatOptionsFactory;
    private final AiChatProperties aiChatProperties;
    private final ConversationHistory conversationHistory;
    private final ChatResultFactory chatResultFactory;

    ChatResponseComposer(
            ChatClient chatClient,
            PromptBuilder promptBuilder,
            AiResponseParser aiResponseParser,
            ResponseGroundingEnforcer responseGroundingEnforcer,
            ResponseSanitizer responseSanitizer,
            SessionTurnRecorder sessionTurnRecorder,
            ChatTelemetryService chatTelemetryService,
            PromptCatalog promptCatalog,
            AiChatOptionsFactory chatOptionsFactory,
            AiChatProperties aiChatProperties,
            ConversationHistory conversationHistory,
            ChatResultFactory chatResultFactory) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.aiResponseParser = aiResponseParser;
        this.responseGroundingEnforcer = responseGroundingEnforcer;
        this.responseSanitizer = responseSanitizer;
        this.sessionTurnRecorder = sessionTurnRecorder;
        this.chatTelemetryService = chatTelemetryService;
        this.promptCatalog = promptCatalog;
        this.chatOptionsFactory = chatOptionsFactory;
        this.aiChatProperties = aiChatProperties;
        this.conversationHistory = conversationHistory;
        this.chatResultFactory = chatResultFactory;
    }

    AiChatResult generateAiResponse(
            ChatContext ctx,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<LinkCard> links,
            List<RetrievedChunk> ragChunks) {
        try {
            String systemPrompt = promptBuilder.build(topicLabels, links, ctx.isGeorgian(), ragChunks);
            Prompt prompt = buildGeminiPrompt(systemPrompt, ctx);
            String raw = chatClient.prompt(prompt).call().content();
            return aiResponseParser.parseMainResponse(raw, links, ctx.isGeorgian());
        } catch (Exception e) {
            log.error("AI generation failed: {}", e.getMessage());
            return aiResponseParser.fallback(ctx.isGeorgian(), links, ragChunks);
        }
    }

    Prompt buildGeminiPrompt(String systemPrompt, ChatContext ctx) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(ctx.history());
        messages.add(new UserMessage(ctx.message()));
        return new Prompt(messages, chatOptionsFactory.mainChat());
    }

    ChatResult respond(
            ChatContext ctx,
            String intro,
            List<LinkedExplanation> items,
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<RetrievedChunk> ragChunks,
            ChatResponseKind kind) {
        List<LinkedExplanation> enforcedItems = responseGroundingEnforcer.enforce(items, ragChunks);
        String sanitized = responseSanitizer.strip(intro, ctx.isGeorgian());
        addToHistory(ctx, sanitized, enforcedItems, topics, ragChunks);
        String turnId = UUID.randomUUID().toString();
        chatTelemetryService.recordTurn(
                turnId,
                ctx.sessionId(),
                ctx.message(),
                chatTelemetryService.toHits(ragChunks),
                promptCatalog.promptVersion(),
                promptCatalog.promptContentHash());
        return chatResultFactory.build(
                sanitized, enforcedItems, topics, topicLabels,
                ctx.isGeorgian(), ctx.sessionId(), turnId, kind, ragChunks);
    }

    void addToHistory(
            ChatContext ctx,
            String sanitizedIntro,
            List<LinkedExplanation> items,
            List<Topic> topics,
            List<RetrievedChunk> ragChunks) {
        List<String> urls = items.stream()
                .filter(i -> i.link() != null && i.link().url() != null)
                .map(i -> i.link().url())
                .collect(Collectors.toList());
        String assistantTurn = sessionTurnRecorder.formatAssistantTurn(
                sanitizedIntro, urls, topics,
                sessionTurnRecorder.excerptsFromChunks(ragChunks));
        Deque<Message> history = ctx.history();
        history.addLast(new UserMessage(ctx.message()));
        history.addLast(new AssistantMessage(assistantTurn));
        Deque<Message> trimmed = HistoryBudgetTrimmer.trim(history, aiChatProperties.maxHistoryMessages());
        history.clear();
        history.addAll(trimmed);
        conversationHistory.persist(ctx.sessionId(), history);
    }
}
