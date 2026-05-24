package com.geostat.chat.api;

import com.geostat.chat.api.dto.ChatRequest;
import com.geostat.chat.api.dto.ChatResponse;
import com.geostat.chat.api.mapper.ChatApiMapper;
import com.geostat.chat.application.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;
    private final ChatApiMapper chatApiMapper;

    public ChatController(ChatService chatService, ChatApiMapper chatApiMapper) {
        this.chatService = chatService;
        this.chatApiMapper = chatApiMapper;
    }

    @GetMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "") String sessionId,
            @RequestParam(required = false) String locale) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Chat request: {}", message);
        return ResponseEntity.ok(chatApiMapper.toDto(chatService.getChatResponse(message, sessionId, locale)));
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chatPost(@RequestBody ChatRequest request) {
        return chat(request.message(), request.sessionId() != null ? request.sessionId() : "", request.locale());
    }
}
