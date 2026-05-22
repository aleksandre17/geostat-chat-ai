package Chatbot.service;

import Chatbot.catalog.TopicRegistry;
import Chatbot.retrieval.RetrievalContextService;
import Chatbot.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ChatService v7.0 — pure orchestrator.
 *
 * Flow:
 *   1. Build context (message, language, session, history)
 *   2. Small talk → short-circuit
 *   3. Portal list query → short-circuit
 *   4. TopicDetector → topics
 *   5. ResponseBuilder → links
 *   6. AI generates structured JSON { intro, items[] } or fallback
 *   7. Build ChatResponse
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY = 10;

    private final ChatClient chatClient;
    private final TopicDetector topicDetector;
    private final ResponseBuilder responseBuilder;
    private final ConversationHistory conversationHistory;
    private final PromptBuilder promptBuilder;
    private final SmallTalkHandler smallTalkHandler;
    private final ResponseSanitizer responseSanitizer;
    private final ChatResponseFactory chatResponseFactory;
    private final ObjectMapper objectMapper;
    private final StructureLookup structureLookup;
    private final RetrievalContextService retrievalContextService;

    public ChatService(ChatClient chatClient, TopicDetector topicDetector,
                       ResponseBuilder responseBuilder, ConversationHistory conversationHistory,
                       PromptBuilder promptBuilder, SmallTalkHandler smallTalkHandler,
                       ResponseSanitizer responseSanitizer, ChatResponseFactory chatResponseFactory,
                       ObjectMapper objectMapper, StructureLookup structureLookup,
                       RetrievalContextService retrievalContextService) {
        this.chatClient = chatClient;
        this.topicDetector = topicDetector;
        this.responseBuilder = responseBuilder;
        this.conversationHistory = conversationHistory;
        this.promptBuilder = promptBuilder;
        this.smallTalkHandler = smallTalkHandler;
        this.responseSanitizer = responseSanitizer;
        this.chatResponseFactory = chatResponseFactory;
        this.objectMapper = objectMapper;
        this.structureLookup = structureLookup;
        this.retrievalContextService = retrievalContextService;
        log.info("ChatService v7.0 initialized");
    }

    // ─── Main entry point ────────────────────────────────────────────────────

    public ChatResponse getChatResponse(String userMessage, String sessionId) {
        ChatContext ctx = buildContext(userMessage, sessionId);
        try {
            String smallTalk = smallTalkHandler.handle(ctx.message(), ctx.isGeorgian());
            if (smallTalk != null)
                return respond(ctx, smallTalk, List.of(), List.of(Topic.GENERAL));

            if (smallTalkHandler.isPortalListQuery(ctx.lowerQuery()))
                return respondWithPortals(ctx);

            List<Topic> topics = topicDetector.detect(ctx.lowerQuery(), ctx.message());
            List<LinkCard> links = responseBuilder.buildLinks(topics, ctx.message(), ctx.isGeorgian());

            if (links.isEmpty()) {
                AiResult clarification = generateClarificationResponse(ctx);
                return respond(ctx, clarification.intro(), clarification.items(), topics);
            }

            AiResult result = generateAiResponse(ctx, topics, links);
            return respond(ctx, result.intro(), result.items(), topics);

        } catch (Exception e) {
            log.error("Error processing chat: {}", e.getMessage(), e);
            return chatResponseFactory.error(ctx.isGeorgian(), ctx.sessionId());
        }
    }

    // ─── Context & response helpers ──────────────────────────────────────────

    private ChatContext buildContext(String userMessage, String sessionId) {
        String trimmed = userMessage.trim();
        String sid = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();
        boolean isGeorgian = detectLanguage(trimmed);
        return new ChatContext(trimmed, trimmed.toLowerCase(), isGeorgian, sid,
                conversationHistory.getOrCreate(sid));
    }

    private ChatResponse respond(ChatContext ctx, String intro, List<LinkedExplanation> items,
                                  List<Topic> topics) {
        String sanitized = responseSanitizer.strip(intro, ctx.isGeorgian());
        addToHistory(ctx.history(), ctx.message(), sanitized);
        return chatResponseFactory.build(sanitized, items, topics, ctx.isGeorgian(), ctx.sessionId());
    }

    private ChatResponse respondWithPortals(ChatContext ctx) {
        log.info("Portal list query: {}", ctx.message());
        List<LinkCard> portals = responseBuilder.buildPortalLinks(ctx.isGeorgian());
        String intro = ctx.isGeorgian()
                ? "საქსტატს აქვს მრავალი ინტერაქტიული პორტალი და კალკულატორი. ქვემოთ ნახავთ სრულ ჩამონათვალს."
                : "GeoStat has many interactive portals and calculators. Below is the full list.";
        return respond(ctx, intro, toUnexplained(portals), List.of(Topic.GENERAL));
    }

    // ─── AI generation ───────────────────────────────────────────────────────

    private static final String CLARIFICATION_PROMPT_KA = """
            შენ ხარ საქსტატის (საქართველოს სტატისტიკის ეროვნული სამსახური) ვირტუალური ასისტენტი.
            მომხმარებლის კითხვა ვერ დადგინდა — ვერ მოიძებნა შესაბამისი სტატისტიკური ინფორმაცია.

            ====== საქსტატის სტრუქტურა ======
            {STRUCTURE}
            =================================

            ## შენი ამოცანა
            1. თუ მომხმარებელი ეძებს კონკრეტულ პიროვნებას ან დეპარტამენტს — მოძებნე სტრუქტურაში.
               თუ იპოვე — item-ად შეიტანე URL და ახსნა.
               თუ ვერ იპოვე — items ცარიელია, intro-ში.
            2. სხვა შემთხვევაში — items ცარიელია, intro-ში დასვი ერთი  კითხვა დაზუსტებისთვის.

            ## გამოსავლის ფორმატი — მხოლოდ JSON (markdown ``` — არა)
            პიროვნება/დეპარტამენტი ნაპოვნია:
            {"intro":" ახსნა ქართულად","items":[{"url":"ზუსტი URL სტრუქტურიდან","title":"სახელი ან დეპარტამენტი","explanation":"დამატებითი ინფო ქართულად"}]}

            ვერ ნახე ან კლარიფიკაცია:
            {"intro":"კითხვა ან შეტყობინება ქართულად","items":[]}

            ## წესები
            - მხოლოდ ქართულად
            - url — ზუსტად სტრუქტურიდან, ნუ შეცვლი
            - ემოჯი — არა; "სიამოვნებით" — არა
            """;

    private static final String CLARIFICATION_PROMPT_EN = """
            You are GeoStat's (National Statistics Office of Georgia) virtual assistant.
            The user's query did not match any known statistical topic.

            ====== GeoStat Organisational Structure ======
            {STRUCTURE}
            ==============================================

            ## Your task
            1. If the user is looking for a person or department — search the structure above.
               If found — add an item with the URL and explanation.
               If not found — items is empty, explain briefly in intro.
            2. Otherwise — items is empty, ask one short clarifying question in intro.

            ## Response format — JSON only (no markdown ```)
            Person/department found:
            {"intro":"brief explanation","items":[{"url":"exact URL from structure","title":"name or department","explanation":"additional info in English"}]}

            Not found or clarification:
            {"intro":"question or message","items":[]}

            ## Rules
            - English only
            - url must be exact from the structure — do not modify
            - No emojis; no "I'd be happy to help"
            """;

    private AiResult generateClarificationResponse(ChatContext ctx) {
        try {
            String structure = structureLookup.get(ctx.isGeorgian());
            String placeholder = structure.isEmpty()
                    ? (ctx.isGeorgian() ? "(სტრუქტურა მიუწვდომელია)" : "(structure unavailable)")
                    : structure;
            String template = ctx.isGeorgian() ? CLARIFICATION_PROMPT_KA : CLARIFICATION_PROMPT_EN;
            String systemPrompt = template.replace("{STRUCTURE}", placeholder);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(ctx.history());
            messages.add(new UserMessage(ctx.message()));
            Prompt prompt = new Prompt(messages, ChatOptions.builder().temperature(0.3).build());
            String raw = chatClient.prompt(prompt).call().content();
            if (raw != null && !raw.isBlank()) return parseClarificationResult(raw, ctx.isGeorgian());
        } catch (Exception e) {
            log.warn("Clarification generation failed: {}", e.getMessage());
        }
        return new AiResult(smallTalkHandler.clarificationRequest(ctx.isGeorgian()), List.of());
    }

    private AiResult parseClarificationResult(String raw, boolean isGeorgian) {
        try {
            JsonNode root = objectMapper.readTree(stripMarkdown(raw));
            String intro = root.path("intro").asText("").strip();

            List<LinkedExplanation> items = new ArrayList<>();
            for (JsonNode node : root.path("items")) {
                String url   = node.path("url").asText("").strip();
                String title = node.path("title").asText("").strip();
                String expl  = node.path("explanation").asText("").strip();
                if (url.isEmpty()) continue;
                TopicDefinition.TopicStyle style = TopicRegistry.get(Topic.STRUCTURE).style();
                String label = title.isEmpty() ? expl : title;
                LinkCard card = new LinkCard(url, label, label,
                        "general", style.icon(), "", style.bgColor());
                items.add(new LinkedExplanation(expl.isEmpty() ? null : expl, card));
            }

            if (intro.isEmpty())
                intro = isGeorgian ? "იხილეთ შემდეგი ინფორმაცია." : "See the following information.";

            // No structure hits (clarification case) → append contact/sitemap as footer
            if (items.isEmpty()) items = toUnexplained(responseBuilder.buildFallbackLinks());

            return new AiResult(intro, items);
        } catch (Exception e) {
            log.warn("Failed to parse clarification JSON, returning as plain text: {}", e.getMessage());
            return new AiResult(raw.strip(), toUnexplained(responseBuilder.buildFallbackLinks()));
        }
    }

    private record AiResult(String intro, List<LinkedExplanation> items) {}

    private AiResult generateAiResponse(ChatContext ctx, List<Topic> topics, List<LinkCard> links) {
        try {
            String systemPrompt = promptBuilder.build(
                    topics, links, ctx.isGeorgian(), retrievalContextService.retrieve(ctx.message(), ctx.isGeorgian()));
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(new ArrayList<>(ctx.history()));
            messages.add(new UserMessage(ctx.message()));
            Prompt prompt = new Prompt(messages, ChatOptions.builder().temperature(0.6).build());
            String raw = chatClient.prompt(prompt).call().content();
            return parseAiResult(raw, links, ctx.isGeorgian());
        } catch (Exception e) {
            log.error("AI generation failed: {}", e.getMessage());
            return fallbackResult(ctx.isGeorgian(), links);
        }
    }

    private AiResult parseAiResult(String raw, List<LinkCard> links, boolean isGeorgian) {
        try {
            String json = stripMarkdown(raw);
            JsonNode root = objectMapper.readTree(json);
            String intro = root.path("intro").asText("").strip();

            Map<String, LinkCard> byUrl = links.stream()
                    .collect(Collectors.toMap(LinkCard::url, l -> l, (a, b) -> a));

            List<LinkedExplanation> items = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            for (JsonNode node : root.path("items")) {
                String url  = node.path("url").asText("").strip();
                String expl = node.path("explanation").asText("").strip();
                LinkCard card = byUrl.get(url);
                if (card != null && seen.add(url)) {
                    items.add(new LinkedExplanation(expl.isEmpty() ? null : expl, card));
                }
            }

            // Append any links the AI accidentally omitted
            for (LinkCard card : links) {
                if (seen.add(card.url())) {
                    items.add(new LinkedExplanation(null, card));
                }
            }

            if (items.isEmpty()) return fallbackResult(isGeorgian, links);

            if (intro.isEmpty())
                intro = isGeorgian ? "იხილეთ შემდეგი რესურსები." : "See the following resources.";

            return new AiResult(intro, items);

        } catch (Exception e) {
            log.warn("Failed to parse AI JSON response, using fallback: {}", e.getMessage());
            return fallbackResult(isGeorgian, links);
        }
    }

    private String stripMarkdown(String raw) {
        if (raw == null) return "{}";
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").strip();
        }
        return s;
    }

    private AiResult fallbackResult(boolean isGeorgian, List<LinkCard> links) {
        String intro = isGeorgian
                ? "მოთხოვნილი ინფორმაცია იხილეთ ქვემოთ მოცემულ ბმულებზე."
                : "You'll find the requested information at the links below.";
        return new AiResult(intro, toUnexplained(links));
    }

    private List<LinkedExplanation> toUnexplained(List<LinkCard> links) {
        return links.stream().map(l -> new LinkedExplanation(null, l)).toList();
    }

    // ─── History ─────────────────────────────────────────────────────────────

    private void addToHistory(Deque<Message> history, String userMsg, String assistantMsg) {
        history.addLast(new UserMessage(userMsg));
        history.addLast(new AssistantMessage(assistantMsg));
        while (history.size() > MAX_HISTORY) history.pollFirst();
    }

    // ─── Language detection ──────────────────────────────────────────────────

    private boolean detectLanguage(String text) {
        long geoChars   = text.chars().filter(ch -> ch >= 0x10D0 && ch <= 0x10FF).count();
        long latinChars = text.chars().filter(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')).count();
        return geoChars >= latinChars;
    }
}