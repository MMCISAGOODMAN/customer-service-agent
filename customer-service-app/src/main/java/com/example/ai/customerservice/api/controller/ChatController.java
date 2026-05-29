package com.example.ai.customerservice.api.controller;

import com.example.ai.customerservice.application.service.AgentOrchestrator;
import com.example.ai.customerservice.application.service.StreamingChatService;
import com.example.ai.customerservice.api.dto.ChatRequest;
import com.example.ai.customerservice.api.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final AgentOrchestrator agentOrchestrator;
    private final StreamingChatService streamingChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();
        return streamingChatService.stream(sessionId, request.getMessage());
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();
        ChatResponse response = agentOrchestrator.process(sessionId, request.getMessage())
                .withSessionId(sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat")
    public ResponseEntity<ChatResponse> chatGet(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setSessionId(sessionId);
        return chat(request);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "customer-service-agent"));
    }
}
