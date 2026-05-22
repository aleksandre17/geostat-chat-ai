package Chatbot.controller;

import Chatbot.model.ChatRequest;
import Chatbot.model.ChatResponse;
import Chatbot.model.Topic;
import Chatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "") String sessionId) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (message.equals("კაციტაძე") || message.equalsIgnoreCase("Katsitadze")) {
            return ResponseEntity.ok(new ChatResponse(
                    "ნინი, 20 წლის გოგოსავით გამოიყურები. <3",
                    null,
                    "ka",
                    "FLIRTING",
                    null,
                    "heart",
                    "",
                    "#ffcccc",
                    sessionId
            ));
        }

        log.info("Chat request: {}", message);
        return ResponseEntity.ok(chatService.getChatResponse(message, sessionId));
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chatPost(@RequestBody ChatRequest request) {
        return chat(request.message(), request.sessionId() != null ? request.sessionId() : "");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"ok\",\"service\":\"GeoStat Chatbot\"}");
    }
}