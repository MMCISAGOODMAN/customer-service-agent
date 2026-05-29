package com.example.customerservice.agent;

import com.example.customerservice.dto.ChatResponse;
import com.example.customerservice.fallback.RuleBasedFallbackService;
import com.example.customerservice.observability.ReactStepLogger;
import com.example.customerservice.observability.ReactTraceContext;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientAiClient {

    private final CustomerServiceAgent customerServiceAgent;
    private final RuleBasedFallbackService fallbackService;
    private final ReactStepLogger reactStepLogger;

    @CircuitBreaker(name = "aiAgent", fallbackMethod = "circuitBreakerFallback")
    public ChatResponse invoke(String sessionId, String message) {
        reactStepLogger.startTrace(sessionId, message);
        try {
            Result<String> result = customerServiceAgent.chat(sessionId, message);
            List<ToolExecution> toolExecutions = result.toolExecutions();
            int reactRounds = toolExecutions != null ? toolExecutions.size() : 0;

            reactStepLogger.finishTrace(result.content(), reactRounds);

            return ChatResponse.builder()
                    .reply(result.content())
                    .mode("react")
                    .fallback(false)
                    .reactRounds(reactRounds)
                    .toolCalls(summarizeToolCalls(toolExecutions))
                    .build();
        } catch (Exception e) {
            ReactTraceContext.clear();
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private ChatResponse circuitBreakerFallback(String sessionId, String message, Throwable t) {
        ReactTraceContext.clear();
        log.warn("Circuit breaker open, falling back to rule-based service: {}", t.getMessage());
        return ChatResponse.fallback(fallbackService.handle(message), t.getMessage());
    }

    private List<String> summarizeToolCalls(List<ToolExecution> executions) {
        if (executions == null || executions.isEmpty()) {
            return List.of();
        }
        return executions.stream()
                .map(e -> e.request().name() + "(" + e.request().arguments() + ")")
                .toList();
    }
}
