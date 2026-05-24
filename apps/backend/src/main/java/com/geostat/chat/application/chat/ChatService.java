package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.CatalogRagLinkMerger;
import com.geostat.chat.application.retrieval.RetrievalContextService;
import com.geostat.chat.application.telemetry.ChatTelemetryService;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.chat.ChatContext;
import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.session.ConversationHistory;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final TopicDetector topicDetector;
    private final ResponseBuilder responseBuilder;
    private final ConversationHistory conversationHistory;
    private final PromptBuilder promptBuilder;
    private final SmallTalkHandler smallTalkHandler;
    private final ResponseSanitizer responseSanitizer;
    private final ChatResultFactory chatResultFactory;
    private final ChatCompleteEncoder chatCompleteEncoder;
    private final RetrievalContextService retrievalContextService;
    private final CatalogRagLinkMerger catalogRagLinkMerger;
    private final ChatTelemetryService chatTelemetryService;
    private final AiResponseParser aiResponseParser;
    private final ClarificationService clarificationService;
    private final ChatLanguageDetector languageDetector;
    private final AiChatOptionsFactory chatOptionsFactory;
    private final AiChatProperties aiChatProperties;
    private final ResponseGroundingEnforcer responseGroundingEnforcer;
    private final QueryRouter queryRouter;
    private final PromptCatalog promptCatalog;

    public ChatService(
            ChatClient chatClient,
            TopicDetector topicDetector,
            ResponseBuilder responseBuilder,
            ConversationHistory conversationHistory,
            PromptBuilder promptBuilder,
            SmallTalkHandler smallTalkHandler,
            ResponseSanitizer responseSanitizer,
            ChatResultFactory chatResultFactory,
            ChatCompleteEncoder chatCompleteEncoder,
            RetrievalContextService retrievalContextService,
            CatalogRagLinkMerger catalogRagLinkMerger,
            ChatTelemetryService chatTelemetryService,
            AiResponseParser aiResponseParser,
            ClarificationService clarificationService,
            ChatLanguageDetector languageDetector,
            AiChatOptionsFactory chatOptionsFactory,
            AiChatProperties aiChatProperties,
            ResponseGroundingEnforcer responseGroundingEnforcer,
            QueryRouter queryRouter,
            PromptCatalog promptCatalog) {
        this.chatClient = chatClient;
        this.topicDetector = topicDetector;
        this.responseBuilder = responseBuilder;
        this.conversationHistory = conversationHistory;
        this.promptBuilder = promptBuilder;
        this.smallTalkHandler = smallTalkHandler;
        this.responseSanitizer = responseSanitizer;
        this.chatResultFactory = chatResultFactory;
        this.chatCompleteEncoder = chatCompleteEncoder;
        this.retrievalContextService = retrievalContextService;
        this.catalogRagLinkMerger = catalogRagLinkMerger;
        this.chatTelemetryService = chatTelemetryService;
        this.aiResponseParser = aiResponseParser;
        this.clarificationService = clarificationService;
        this.languageDetector = languageDetector;
        this.chatOptionsFactory = chatOptionsFactory;
        this.aiChatProperties = aiChatProperties;
        this.responseGroundingEnforcer = responseGroundingEnforcer;
        this.queryRouter = queryRouter;
        this.promptCatalog = promptCatalog;
    }

    public ChatResult getChatResponse(String userMessage, String sessionId) {
        return getChatResponse(userMessage, sessionId, null);
    }

    public ChatResult getChatResponse(String userMessage, String sessionId, String localeHint) {
        ChatContext ctx = buildContext(userMessage, sessionId, localeHint);
        try {
            String smallTalk = smallTalkHandler.handle(ctx.message(), ctx.isGeorgian());
            if (smallTalk != null) {
                return respond(ctx, smallTalk, List.of(), List.of(Topic.GENERAL), List.of(), ChatResponseKind.smalltalk);
            }
            if (smallTalkHandler.isPortalListQuery(ctx.lowerQuery())) {
                return respondWithPortals(ctx);
            }

            List<Topic> topics = topicDetector.detect(ctx.lowerQuery(), ctx.message(), ctx.history());
            List<RetrievedChunk> ragChunks = retrievalContextService.retrieve(ctx.message(), ctx.locale());
            List<LinkCard> links = mergedLinks(topics, ctx, ragChunks);

            if (links.isEmpty()) {
                List<RetrievedChunk> corpusContext = ragChunks.isEmpty()
                        ? retrievalContextService.retrieveForClarification(ctx.message(), ctx.locale())
                        : ragChunks;
                AiChatResult clarification = clarificationService.generate(ctx, corpusContext);
                return respond(
                        ctx,
                        clarification.intro(),
                        clarification.items(),
                        topics,
                        corpusContext,
                        ChatResponseKind.clarification);
            }

            AiChatResult result = generateAiResponse(ctx, topics, links, ragChunks);
            return respond(ctx, result.intro(), result.items(), topics, ragChunks, ChatResponseKind.answer);
        } catch (Exception e) {
            log.error("Error processing chat: {}", e.getMessage(), e);
            return chatResultFactory.error(ctx.isGeorgian(), ctx.sessionId());
        }
    }

    public Flux<ServerSentEvent<String>> streamChatResponse(String userMessage, String sessionId) {
        return streamChatResponse(userMessage, sessionId, null);
    }

    public Flux<ServerSentEvent<String>> streamChatResponse(String userMessage, String sessionId, String localeHint) {
        ChatContext ctx = buildContext(userMessage, sessionId, localeHint);
        try {
            String smallTalk = smallTalkHandler.handle(ctx.message(), ctx.isGeorgian());
            if (smallTalk != null) {
                return Flux.just(completeEvent(
                        respond(ctx, smallTalk, List.of(), List.of(Topic.GENERAL), List.of(), ChatResponseKind.smalltalk)));
            }
            if (smallTalkHandler.isPortalListQuery(ctx.lowerQuery())) {
                return Flux.just(completeEvent(respondWithPortals(ctx)));
            }

            List<Topic> topics = topicDetector.detect(ctx.lowerQuery(), ctx.message(), ctx.history());
            List<RetrievedChunk> ragChunks = retrievalContextService.retrieve(ctx.message(), ctx.locale());
            List<LinkCard> links = mergedLinks(topics, ctx, ragChunks);

            if (links.isEmpty()) {
                List<RetrievedChunk> corpusContext = ragChunks.isEmpty()
                        ? retrievalContextService.retrieveForClarification(ctx.message(), ctx.locale())
                        : ragChunks;
                AiChatResult clarification = clarificationService.generate(ctx, corpusContext);
                return Flux.just(completeEvent(respond(
                        ctx,
                        clarification.intro(),
                        clarification.items(),
                        topics,
                        corpusContext,
                        ChatResponseKind.clarification)));
            }

            String systemPrompt = promptBuilder.build(topics, links, ctx.isGeorgian(), ragChunks);
            Prompt prompt = buildGeminiPrompt(systemPrompt, ctx);

            StringBuilder buffer = new StringBuilder();
            StringBuilder lastIntroSent = new StringBuilder();
            return chatClient.prompt(prompt).stream().content()
                    .doOnNext(chunk -> buffer.append(chunk != null ? chunk : ""))
                    .mapNotNull(chunk -> {
                        String intro = StreamIntroExtractor.extractIntro(buffer.toString());
                        if (intro.isEmpty() || intro.contentEquals(lastIntroSent)) {
                            return null;
                        }
                        lastIntroSent.setLength(0);
                        lastIntroSent.append(intro);
                        return ServerSentEvent.<String>builder().event("token").data(intro).build();
                    })
                    .concatWith(Mono.fromCallable(() -> {
                        AiChatResult result = aiResponseParser.parseMainResponse(
                                buffer.toString(), links, ctx.isGeorgian());
                        return completeEvent(respond(
                                ctx, result.intro(), result.items(), topics, ragChunks, ChatResponseKind.answer));
                    }).flatMapMany(Flux::just));
        } catch (Exception e) {
            log.error("Stream chat error: {}", e.getMessage(), e);
            return Flux.just(completeEvent(chatResultFactory.error(ctx.isGeorgian(), ctx.sessionId())));
        }
    }

    private List<LinkCard> mergedLinks(List<Topic> topics, ChatContext ctx, List<RetrievedChunk> ragChunks) {
        List<LinkCard> catalogLinks = responseBuilder.buildLinks(topics, ctx.message(), ctx.isGeorgian());
        int maxRag = switch (ctx.intent()) {
            case CONCEPT -> CatalogRagLinkMerger.MAX_RAG;
            case DATA_REQUEST, NAVIGATE -> 2;
            case CLARIFY -> 1;
        };
        return catalogRagLinkMerger.merge(catalogLinks, ragChunks, ctx.isGeorgian(), maxRag);
    }

    private ChatContext buildContext(String userMessage, String sessionId, String localeHint) {
        String trimmed = userMessage.trim();
        String sid = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();
        String locale = languageDetector.resolveLocale(trimmed, localeHint);
        boolean isGeorgian = "ka".equals(locale);
        QueryIntent intent = queryRouter.route(trimmed, trimmed.toLowerCase());
        Deque<Message> history = HistoryBudgetTrimmer.trim(
                conversationHistory.getOrCreate(sid), aiChatProperties.maxHistoryMessages());
        return new ChatContext(trimmed, trimmed.toLowerCase(), isGeorgian, locale, intent, sid, history);
    }

    private AiChatResult generateAiResponse(
            ChatContext ctx, List<Topic> topics, List<LinkCard> links, List<RetrievedChunk> ragChunks) {
        try {
            String systemPrompt = promptBuilder.build(topics, links, ctx.isGeorgian(), ragChunks);
            Prompt prompt = buildGeminiPrompt(systemPrompt, ctx);
            String raw = chatClient.prompt(prompt).call().content();
            return aiResponseParser.parseMainResponse(raw, links, ctx.isGeorgian());
        } catch (Exception e) {
            log.error("AI generation failed: {}", e.getMessage());
            return aiResponseParser.fallback(ctx.isGeorgian(), links);
        }
    }

    private Prompt buildGeminiPrompt(String systemPrompt, ChatContext ctx) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(HistoryBudgetTrimmer.trim(ctx.history(), aiChatProperties.maxHistoryMessages()));
        messages.add(new UserMessage(ctx.message()));
        return new Prompt(messages, chatOptionsFactory.mainChat());
    }

    private ServerSentEvent<String> completeEvent(ChatResult result) {
        return ServerSentEvent.<String>builder()
                .event("complete")
                .data(chatCompleteEncoder.encodeComplete(result))
                .build();
    }

    private ChatResult respond(
            ChatContext ctx,
            String intro,
            List<LinkedExplanation> items,
            List<Topic> topics,
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
                sanitized, enforcedItems, topics, ctx.isGeorgian(), ctx.sessionId(), turnId, kind, ragChunks);
    }

    private ChatResult respondWithPortals(ChatContext ctx) {
        log.info("Portal list query: {}", ctx.message());
        List<LinkCard> portals = responseBuilder.buildPortalLinks(ctx.isGeorgian());
        String intro = ctx.isGeorgian()
                ? "საქსტატს აქვს მრავალი ინტერაქტიული პორტალი და კალკულატორი. ქვემოთ ნახავთ სრულ ჩამონათვალს."
                : "GeoStat has many interactive portals and calculators. Below is the full list.";
        List<LinkedExplanation> portalItems = portals.stream().map(l -> new LinkedExplanation(null, l)).toList();
        return respond(ctx, intro, portalItems, List.of(Topic.GENERAL), List.of(), ChatResponseKind.portal_list);
    }

    private void addToHistory(
            ChatContext ctx,
            String sanitizedIntro,
            List<LinkedExplanation> items,
            List<Topic> topics,
            List<RetrievedChunk> ragChunks) {
        List<String> urls = items.stream()
                .filter(i -> i.link() != null && i.link().url() != null)
                .map(i -> i.link().url())
                .collect(Collectors.toList());
        String assistantTurn = SessionTurnRecorder.formatAssistantTurn(
                sanitizedIntro,
                urls,
                topics,
                SessionTurnRecorder.excerptsFromChunks(ragChunks));
        Deque<Message> history = ctx.history();
        history.addLast(new UserMessage(ctx.message()));
        history.addLast(new AssistantMessage(assistantTurn));
        Deque<Message> trimmed = HistoryBudgetTrimmer.trim(history, aiChatProperties.maxHistoryMessages());
        history.clear();
        history.addAll(trimmed);
        conversationHistory.persist(ctx.sessionId(), history);
    }
}
