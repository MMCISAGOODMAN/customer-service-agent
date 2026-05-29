package com.example.customerservice.controller;

import com.example.customerservice.agent.AgentOrchestrator;
import com.example.customerservice.dto.ChatRequest;
import com.example.customerservice.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final AgentOrchestrator agentOrchestrator;

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
