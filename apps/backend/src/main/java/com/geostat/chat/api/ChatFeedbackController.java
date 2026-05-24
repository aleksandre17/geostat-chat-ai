package com.geostat.chat.api;

import com.geostat.chat.api.dto.ChatFeedbackRequest;
import com.geostat.chat.api.mapper.ChatApiMapper;
import com.geostat.chat.application.telemetry.ChatTelemetryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatFeedbackController {

    private final ChatTelemetryService chatTelemetryService;
    private final ChatApiMapper chatApiMapper;

    public ChatFeedbackController(ChatTelemetryService chatTelemetryService, ChatApiMapper chatApiMapper) {
        this.chatTelemetryService = chatTelemetryService;
        this.chatApiMapper = chatApiMapper;
    }

    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> feedback(@RequestBody ChatFeedbackRequest request) {
        try {
            chatTelemetryService.recordFeedback(chatApiMapper.toCommand(request));
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
