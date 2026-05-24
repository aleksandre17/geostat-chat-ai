package com.geostat.chat.api;

import com.geostat.chat.application.chat.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import com.geostat.chat.api.dto.ChatRequest;

@RestController
@RequestMapping("/api")
public class ChatStreamController {

    private final ChatService chatService;

    public ChatStreamController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamGet(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "") String sessionId,
            @RequestParam(required = false) String locale) {
        return chatService.streamChatResponse(message, sessionId, locale);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamPost(@RequestBody ChatRequest request) {
        String sid = request.sessionId() != null ? request.sessionId() : "";
        return chatService.streamChatResponse(request.message(), sid, request.locale());
    }
}
