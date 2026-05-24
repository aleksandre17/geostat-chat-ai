package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.geostat.chat.application.retrieval.CatalogRagLinkMerger;
import com.geostat.chat.application.retrieval.RetrievalContextService;
import com.geostat.chat.application.telemetry.ChatTelemetryService;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.session.ConversationHistory;
import com.geostat.chat.infrastructure.config.AiChatOptionsFactory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatClient chatClient;
    @Mock TopicDetector topicDetector;
    @Mock ResponseBuilder responseBuilder;
    @Mock ConversationHistory conversationHistory;
    @Mock PromptBuilder promptBuilder;
    @Mock SmallTalkHandler smallTalkHandler;
    @Mock ResponseSanitizer responseSanitizer;
    @Mock ChatResultFactory chatResultFactory;
    @Mock ChatCompleteEncoder chatCompleteEncoder;
    @Mock RetrievalContextService retrievalContextService;
    @Mock CatalogRagLinkMerger catalogRagLinkMerger;
    @Mock ChatTelemetryService chatTelemetryService;
    @Mock AiResponseParser aiResponseParser;
    @Mock ClarificationService clarificationService;
    @Mock ChatLanguageDetector languageDetector;
    @Mock AiChatOptionsFactory chatOptionsFactory;
    @Mock ResponseGroundingEnforcer responseGroundingEnforcer;
    @Mock QueryRouter queryRouter;
    @Mock PromptCatalog promptCatalog;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        AiChatProperties props = new AiChatProperties(0.6, 0.3, 0.0, 10, 12000, 28000, 2048, 30, true);
        chatService = new ChatService(
                chatClient,
                topicDetector,
                responseBuilder,
                conversationHistory,
                promptBuilder,
                smallTalkHandler,
                responseSanitizer,
                chatResultFactory,
                chatCompleteEncoder,
                retrievalContextService,
                catalogRagLinkMerger,
                chatTelemetryService,
                aiResponseParser,
                clarificationService,
                languageDetector,
                chatOptionsFactory,
                props,
                responseGroundingEnforcer,
                queryRouter,
                promptCatalog);
    }

    @Test
    void getChatResponse_smallTalk_skipsGeminiAndRecordsTelemetry() {
        when(languageDetector.resolveLocale("გამარჯობა", null)).thenReturn("ka");
        when(queryRouter.route(anyString(), anyString())).thenReturn(QueryIntent.NAVIGATE);
        when(conversationHistory.getOrCreate(anyString())).thenReturn(new ArrayDeque<>());
        when(smallTalkHandler.handle("გამარჯობა", true)).thenReturn("გამარჯობა!");
        when(responseSanitizer.strip("გამარჯობა!", true)).thenReturn("გამარჯობა!");
        when(responseGroundingEnforcer.enforce(anyList(), anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(promptCatalog.promptVersion()).thenReturn(1);
        when(promptCatalog.promptContentHash()).thenReturn("abc123");
        when(chatResultFactory.build(any(), anyList(), anyList(), eq(true), anyString(), anyString(), any(), anyList()))
                .thenReturn(new ChatResult(
                        "გამარჯობა!",
                        List.of(),
                        "ka",
                        Topic.GENERAL.name(),
                        List.of(Topic.GENERAL.name()),
                        "i",
                        "#fff",
                        "sess",
                        "turn",
                        ChatResponseKind.smalltalk,
                        false,
                        0,
                        null,
                        null));

        ChatResult result = chatService.getChatResponse("გამარჯობა", "sess");

        assertEquals("გამარჯობა!", result.intro());
        verifyNoInteractions(chatClient);
        verify(chatTelemetryService)
                .recordTurn(anyString(), eq("sess"), eq("გამარჯობა"), anyList(), eq(1), eq("abc123"));
        verifyNoInteractions(topicDetector);
    }
}
