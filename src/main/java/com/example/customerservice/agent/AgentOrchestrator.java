package com.example.customerservice.agent;

import com.example.customerservice.dto.ChatResponse;
import com.example.customerservice.fallback.RuleBasedFallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ResilientAiClient resilientAiClient;
    private final RuleBasedFallbackService fallbackService;

    public ChatResponse process(String sessionId, String message) {
        try {
            return resilientAiClient.invoke(sessionId, message);
        } catch (Exception e) {
            log.error("Agent processing failed, using fallback: {}", e.getMessage());
            return ChatResponse.fallback(fallbackService.handle(message), e.getMessage());
        }
    }
}
