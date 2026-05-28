package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.geostat.chat.application.query.QueryIntentMapper;
import com.geostat.chat.application.query.QueryUnderstandingPipeline;
import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.application.retrieval.ResponseRouter;
import com.geostat.chat.application.retrieval.RetrievalContextService;
import com.geostat.chat.domain.catalog.CatalogResponseAssembler;
import com.geostat.chat.domain.retrieval.RetrievalConfidenceAssessor;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.chat.domain.prompt.PromptCatalog;
import com.geostat.chat.domain.query.SpellFixer;
import com.geostat.chat.domain.session.ConversationHistory;
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

    @Mock ChatPipelineCoordinator pipeline;
    @Mock ChatResponseComposer composer;
    @Mock ChatClient chatClient;
    @Mock PromptBuilder promptBuilder;
    @Mock ChatCompleteEncoder chatCompleteEncoder;
    @Mock ChatResultFactory chatResultFactory;
    @Mock CatalogResponseAssembler catalogResponseAssembler;
    @Mock PromptCatalog promptCatalog;
    @Mock ChatLanguageDetector languageDetector;
    @Mock QueryRouter queryRouter;
    @Mock QueryUnderstandingProperties queryUnderstandingProperties;
    @Mock QueryUnderstandingPipeline queryUnderstandingPipeline;
    @Mock QueryIntentMapper queryIntentMapper;
    @Mock SpellFixer spellFixer;
    @Mock ConversationHistory conversationHistory;
    @Mock AiResponseParser aiResponseParser;
    @Mock RetrievalConfidenceAssessor confidenceAssessor;
    @Mock ResponseRouter responseRouter;
    @Mock ClarificationService clarificationService;
    @Mock RetrievalContextService retrievalContextService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        AiChatProperties props = new AiChatProperties(0.6, 0.3, 0.0, 10, 12000, 28000, 2048, 30, true);
        when(queryUnderstandingProperties.isEnabled()).thenReturn(false);
        when(spellFixer.fix(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(0));
        chatService = new ChatService(
                pipeline,
                composer,
                chatClient,
                promptBuilder,
                chatCompleteEncoder,
                chatResultFactory,
                catalogResponseAssembler,
                promptCatalog,
                languageDetector,
                queryRouter,
                queryUnderstandingProperties,
                queryUnderstandingPipeline,
                queryIntentMapper,
                spellFixer,
                conversationHistory,
                props,
                aiResponseParser,
                confidenceAssessor,
                responseRouter,
                clarificationService,
                retrievalContextService);
    }

    @Test
    void getChatResponse_smallTalk_skipsGeminiAndHistory() {
        when(languageDetector.resolveLocale("გამარჯობა", null)).thenReturn("ka");
        when(queryRouter.route(anyString(), anyString())).thenReturn(QueryIntent.NAVIGATE);
        when(conversationHistory.getOrCreate(anyString())).thenReturn(new ArrayDeque<>());
        when(pipeline.checkEarlyExit(any()))
                .thenReturn(new ChatPipelineCoordinator.EarlyExit.SmallTalk("გამარჯობა!"));
        when(chatResultFactory.build(any(), anyList(), anyList(), any(), eq(true), anyString(), anyString(), any(), anyList()))
                .thenReturn(new ChatResult(
                        "გამარჯობა!",
                        List.of(),
                        "ka",
                        "ზოგადი",
                        List.of(),
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
        verifyNoInteractions(composer);
        verify(pipeline, never()).buildRetrieval(any());
    }
}
