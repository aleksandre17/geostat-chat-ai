package com.geostat.chat.application.chat;

import com.geostat.chat.application.query.QueryIntentMapper;
import com.geostat.chat.application.query.QueryUnderstandingPipeline;
import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.application.retrieval.ResponseRouter;
import com.geostat.chat.application.retrieval.RetrievalContextService;
import com.geostat.chat.domain.catalog.CatalogResponseAssembler;
import com.geostat.chat.domain.retrieval.ResponseRoute;
import com.geostat.chat.domain.retrieval.RetrievalConfidenceAssessor;
import com.geostat.platform.retrieval.RetrievalConfidence;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.chat.AiChatResult;
import com.geostat.chat.domain.chat.ChatContext;
import com.geostat.chat.domain.chat.PipelineResult;
import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.prompt.UiStringKey;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.chat.domain.query.SpellFixer;
import com.geostat.chat.domain.session.ConversationHistory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatPipelineCoordinator pipeline;
    private final ChatResponseComposer composer;
    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final ChatCompleteEncoder chatCompleteEncoder;
    private final ChatResultFactory chatResultFactory;
    private final CatalogResponseAssembler catalogResponseAssembler;
    private final PromptCatalog promptCatalog;
    private final ChatLanguageDetector languageDetector;
    /** Used only when query-understanding.enabled=false (legacy fallback). */
    private final QueryRouter queryRouter;
    private final QueryUnderstandingProperties queryUnderstandingProperties;
    private final QueryUnderstandingPipeline queryUnderstandingPipeline;
    private final QueryIntentMapper queryIntentMapper;
    private final SpellFixer spellFixer;
    private final ConversationHistory conversationHistory;
    private final AiChatProperties aiChatProperties;
    private final AiResponseParser aiResponseParser;
    private final RetrievalConfidenceAssessor confidenceAssessor;
    private final ResponseRouter responseRouter;
    private final ClarificationService clarificationService;
    private final RetrievalContextService retrievalContextService;

    public ChatService(
            ChatPipelineCoordinator pipeline,
            ChatResponseComposer composer,
            ChatClient chatClient,
            PromptBuilder promptBuilder,
            ChatCompleteEncoder chatCompleteEncoder,
            ChatResultFactory chatResultFactory,
            CatalogResponseAssembler catalogResponseAssembler,
            PromptCatalog promptCatalog,
            ChatLanguageDetector languageDetector,
            @Lazy QueryRouter queryRouter,
            QueryUnderstandingProperties queryUnderstandingProperties,
            QueryUnderstandingPipeline queryUnderstandingPipeline,
            QueryIntentMapper queryIntentMapper,
            SpellFixer spellFixer,
            ConversationHistory conversationHistory,
            AiChatProperties aiChatProperties,
            AiResponseParser aiResponseParser,
            RetrievalConfidenceAssessor confidenceAssessor,
            ResponseRouter responseRouter,
            ClarificationService clarificationService,
            RetrievalContextService retrievalContextService) {
        this.pipeline = pipeline;
        this.composer = composer;
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.chatCompleteEncoder = chatCompleteEncoder;
        this.chatResultFactory = chatResultFactory;
        this.catalogResponseAssembler = catalogResponseAssembler;
        this.promptCatalog = promptCatalog;
        this.languageDetector = languageDetector;
        this.queryRouter = queryRouter;
        this.queryUnderstandingProperties = queryUnderstandingProperties;
        this.queryUnderstandingPipeline = queryUnderstandingPipeline;
        this.queryIntentMapper = queryIntentMapper;
        this.spellFixer = spellFixer;
        this.conversationHistory = conversationHistory;
        this.aiChatProperties = aiChatProperties;
        this.aiResponseParser = aiResponseParser;
        this.confidenceAssessor = confidenceAssessor;
        this.responseRouter = responseRouter;
        this.clarificationService = clarificationService;
        this.retrievalContextService = retrievalContextService;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    public ChatResult getChatResponse(String userMessage, String sessionId) {
        return getChatResponse(userMessage, sessionId, null);
    }

    public ChatResult getChatResponse(String userMessage, String sessionId, String localeHint) {
        ChatContext ctx = buildContext(userMessage, sessionId, localeHint);
        try {
            ChatResult early = resolveEarlyExit(ctx);
            if (early != null) {
                return early;
            }

            PipelineResult pipe = routeAfterRetrieval(ctx, pipeline.buildRetrieval(ctx));
            return switch (pipe) {
                case PipelineResult.Ready r -> {
                    AiChatResult result = composer.generateAiResponse(ctx, r.topicLabels(), r.links(), r.ragChunks());
                    yield composer.respond(ctx, result.intro(), result.items(),
                            r.topics(), r.topicLabels(), r.ragChunks(), ChatResponseKind.answer);
                }
                case PipelineResult.NeedsClarification nc ->
                        composer.respond(ctx, nc.result().intro(), nc.result().items(),
                                nc.topics(), nc.topicLabels(), nc.ragChunks(), ChatResponseKind.clarification);
            };
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
            ChatResult early = resolveEarlyExit(ctx);
            if (early != null) {
                return Flux.just(completeEvent(early));
            }

            PipelineResult pipe = routeAfterRetrieval(ctx, pipeline.buildRetrieval(ctx));
            return switch (pipe) {
                case PipelineResult.NeedsClarification nc -> Flux.just(completeEvent(
                        composer.respond(ctx, nc.result().intro(), nc.result().items(),
                                nc.topics(), nc.topicLabels(), nc.ragChunks(), ChatResponseKind.clarification)));
                case PipelineResult.Ready r -> streamReadyPipeline(ctx, r);
            };
        } catch (Exception e) {
            log.error("Stream chat error: {}", e.getMessage(), e);
            return Flux.just(completeEvent(chatResultFactory.error(ctx.isGeorgian(), ctx.sessionId())));
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PipelineResult routeAfterRetrieval(
            ChatContext ctx, ChatPipelineCoordinator.RetrievalOutcome retrieval) {
        RetrievalConfidence confidence = confidenceAssessor.assess(retrieval.ragChunks());
        ResponseRoute route = responseRouter.route(confidence);
        return switch (route) {
            case ANSWER_WITH_CITATIONS, ANSWER_WITH_SUGGESTIONS ->
                    new PipelineResult.Ready(
                            retrieval.topics(),
                            retrieval.topicLabels(),
                            retrieval.links(),
                            retrieval.ragChunks());
            case CLARIFY -> {
                List<RetrievedChunk> corpusContext = retrieval.ragChunks().isEmpty()
                        ? retrievalContextService.retrieveForClarification(ctx.retrievalQuery(), ctx.locale())
                        : retrieval.ragChunks();
                AiChatResult clarification = clarificationService.generate(ctx, corpusContext);
                yield new PipelineResult.NeedsClarification(
                        retrieval.topics(), retrieval.topicLabels(), corpusContext, clarification);
            }
            case REFUSE_SUGGEST_TOPICS ->
                    new PipelineResult.NeedsClarification(
                            retrieval.topics(),
                            retrieval.topicLabels(),
                            List.of(),
                            AiChatResult.emptyIntro(
                                    promptCatalog.uiString(UiStringKey.CLARIFICATION_FALLBACK, ctx.isGeorgian())));
        };
    }

    private ChatResult resolveEarlyExit(ChatContext ctx) {
        ChatPipelineCoordinator.EarlyExit early = pipeline.checkEarlyExit(ctx);
        if (early == null) {
            return null;
        }
        return switch (early) {
            case ChatPipelineCoordinator.EarlyExit.SmallTalk s ->
                    respondSmallTalk(ctx, s.message(), generalLabels(ctx.isGeorgian()));
            case ChatPipelineCoordinator.EarlyExit.PortalList ignored ->
                    respondWithPortals(ctx);
        };
    }

    private Flux<ServerSentEvent<String>> streamReadyPipeline(ChatContext ctx, PipelineResult.Ready r) {
        String systemPrompt = promptBuilder.build(
                r.topicLabels(), r.links(), ctx.isGeorgian(), r.ragChunks());
        Prompt prompt = composer.buildGeminiPrompt(systemPrompt, ctx);

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
                            buffer.toString(), r.links(), ctx.isGeorgian());
                    return completeEvent(composer.respond(ctx, result.intro(), result.items(), r.topics(),
                            r.topicLabels(), r.ragChunks(), ChatResponseKind.answer));
                }).flatMapMany(Flux::just));
    }

    private CatalogTopicLabelResolver.Labels generalLabels(boolean isGeorgian) {
        String label = isGeorgian ? "ზოგადი" : "General";
        return new CatalogTopicLabelResolver.Labels(label, List.of());
    }

    /** Small-talk bypass: returns the result but does NOT add to AI message history. */
    private ChatResult respondSmallTalk(
            ChatContext ctx, String intro, CatalogTopicLabelResolver.Labels labels) {
        return chatResultFactory.build(
                intro, List.of(), List.of(Topic.GENERAL),
                labels, ctx.isGeorgian(), ctx.sessionId(), UUID.randomUUID().toString(),
                ChatResponseKind.smalltalk, List.of());
    }

    private ChatContext buildContext(String userMessage, String sessionId, String localeHint) {
        String trimmed = userMessage.trim();
        String sid = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();
        String locale = languageDetector.resolveLocale(trimmed, localeHint);
        boolean isGeorgian = "ka".equals(locale);
        QueryIntent intent;
        String retrievalQuery;
        AnalyzedQuery analyzedQuery = null;
        if (queryUnderstandingProperties.isEnabled()) {
            AnalyzedQuery analyzed = queryUnderstandingPipeline.analyze(trimmed, locale);
            retrievalQuery = analyzed.retrievalText();
            intent = queryIntentMapper.toChatIntent(analyzed.intent());
            analyzedQuery = analyzed;
        } else {
            retrievalQuery = spellFixer.fix(trimmed, locale);
            intent = queryRouter.route(trimmed, trimmed.toLowerCase());
        }
        Deque<Message> history = HistoryBudgetTrimmer.trim(
                conversationHistory.getOrCreate(sid), aiChatProperties.maxHistoryMessages());
        return new ChatContext(trimmed, trimmed.toLowerCase(), isGeorgian, locale, intent, sid, history, retrievalQuery, analyzedQuery);
    }

    private ServerSentEvent<String> completeEvent(ChatResult result) {
        return ServerSentEvent.<String>builder()
                .event("complete")
                .data(chatCompleteEncoder.encodeComplete(result))
                .build();
    }

    private ChatResult respondWithPortals(ChatContext ctx) {
        log.info("Portal list query: {}", ctx.message());
        List<LinkCard> portals = catalogResponseAssembler.buildPortalLinks(ctx.isGeorgian());
        String intro = promptCatalog.uiString(UiStringKey.PORTAL_LIST_INTRO, ctx.isGeorgian());
        List<LinkedExplanation> portalItems = portals.stream()
                .map(l -> new LinkedExplanation(null, l))
                .toList();
        return composer.respond(ctx, intro, portalItems, List.of(Topic.GENERAL),
                generalLabels(ctx.isGeorgian()), List.of(), ChatResponseKind.portal_list);
    }
}
