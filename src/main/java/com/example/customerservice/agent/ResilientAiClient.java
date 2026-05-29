package com.example.customerservice.agent;

import com.example.customerservice.dto.ChatResponse;
import com.example.customerservice.fallback.RuleBasedFallbackService;
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

    /**
     * ReAct 模式下的重试由 LangChain4j 工具循环内部完成（参数修正、换工具、多轮推理），
     * 此处仅保留熔断降级，避免整次 AI 调用被 Spring Retry 从头重放。
     */
    @CircuitBreaker(name = "aiAgent", fallbackMethod = "circuitBreakerFallback")
    public ChatResponse invoke(String sessionId, String message) {
        log.debug("Invoking ReAct agent for session={}", sessionId);
        Result<String> result = customerServiceAgent.chat(sessionId, message);

        List<ToolExecution> toolExecutions = result.toolExecutions();
        int reactRounds = toolExecutions != null ? toolExecutions.size() : 0;
        log.info("ReAct completed: session={}, toolInvocations={}", sessionId, reactRounds);

        return ChatResponse.builder()
                .reply(result.content())
                .mode("react")
                .fallback(false)
                .reactRounds(reactRounds)
                .toolCalls(summarizeToolCalls(toolExecutions))
                .build();
    }

    @SuppressWarnings("unused")
    private ChatResponse circuitBreakerFallback(String sessionId, String message, Throwable t) {
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
