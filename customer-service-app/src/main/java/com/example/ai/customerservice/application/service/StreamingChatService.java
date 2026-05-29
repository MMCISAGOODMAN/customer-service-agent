package com.example.ai.customerservice.application.service;

import com.example.ai.customerservice.application.agent.CustomerServiceStreamingAgent;
import com.example.ai.customerservice.infrastructure.fallback.RuleBasedFallbackService;
import com.example.ai.observability.ReactStepLogger;
import com.example.ai.observability.ReactStreamPublisher;
import com.example.ai.observability.ReactTraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final CustomerServiceStreamingAgent streamingAgent;
    private final RuleBasedFallbackService fallbackService;
    private final ReactStepLogger reactStepLogger;
    private final ObjectMapper objectMapper;

    public SseEmitter stream(String sessionId, String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicInteger toolCount = new AtomicInteger(0);
        List<String> toolCalls = new ArrayList<>();

        reactStepLogger.startTrace(sessionId, message);
        ReactTraceContext traceContext = ReactTraceContext.current();

        emitter.onTimeout(() -> {
            ReactStreamPublisher.clear();
            ReactTraceContext.clear();
            emitter.complete();
        });
        emitter.onCompletion(() -> {
            ReactStreamPublisher.clear();
            ReactTraceContext.clear();
        });

        CompletableFuture.runAsync(() -> {
            if (traceContext != null) {
                ReactTraceContext.bind(traceContext);
            }
            ReactStreamPublisher.bind(data -> sendEvent(emitter, "react", data));
            try {
                startStream(emitter, sessionId, message, toolCount, toolCalls);
            } finally {
                ReactStreamPublisher.clear();
                ReactTraceContext.clear();
            }
        });

        return emitter;
    }

    private void startStream(SseEmitter emitter, String sessionId, String message,
                             AtomicInteger toolCount, List<String> toolCalls) {
        try {
            streamingAgent.chatStream(sessionId, message)
                    .onPartialResponse(token -> sendEvent(emitter, "token", Map.of("text", token)))
                    .beforeToolExecution(exec -> sendEvent(emitter, "status", Map.of(
                            "phase", "action",
                            "message", "Action：调用 " + exec.request().name(),
                            "tool", exec.request().name()
                    )))
                    .onToolExecuted(exec -> {
                        int n = toolCount.incrementAndGet();
                        toolCalls.add(exec.request().name() + "(" + exec.request().arguments() + ")");
                        sendEvent(emitter, "status", Map.of(
                                "phase", "observation",
                                "message", "Observation #" + n + " 完成，进入下一轮 Thought…",
                                "tool", exec.request().name()
                        ));
                    })
                    .onCompleteResponse(response -> {
                        try {
                            String reply = response.aiMessage() != null ? response.aiMessage().text() : "";
                            int toolInvocations = toolCount.get();
                            int llmRounds = ReactTraceContext.resolveLlmRounds(toolInvocations);

                            Map<String, Object> done = new LinkedHashMap<>();
                            done.put("sessionId", sessionId);
                            done.put("mode", "react");
                            done.put("fallback", false);
                            done.put("reactRounds", llmRounds);
                            done.put("llmRounds", llmRounds);
                            done.put("toolInvocations", toolInvocations);
                            done.put("toolCalls", toolCalls);
                            done.put("reply", reply);
                            sendEvent(emitter, "done", done);
                            reactStepLogger.finishTrace(reply, toolInvocations);
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onError(error -> handleStreamError(emitter, sessionId, message, error))
                    .start();
        } catch (Exception e) {
            handleStreamError(emitter, sessionId, message, e);
        }
    }

    private void handleStreamError(SseEmitter emitter, String sessionId, String message, Throwable error) {
        log.error("Stream chat failed, using fallback: {}", error.getMessage());
        try {
            String fallbackReply = fallbackService.handle(message);
            streamFallbackText(emitter, fallbackReply);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("sessionId", sessionId);
            done.put("mode", "rule-based-fallback");
            done.put("fallback", true);
            done.put("errorDetail", error.getMessage());
            done.put("reply", fallbackReply);
            sendEvent(emitter, "done", done);
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private void streamFallbackText(SseEmitter emitter, String text) {
        sendEvent(emitter, "status", Map.of("phase", "fallback", "message", "AI 不可用，已切换降级模式…"));
        if (text == null || text.isBlank()) {
            return;
        }
        int chunkSize = 4;
        for (int i = 0; i < text.length(); i += chunkSize) {
            String chunk = text.substring(i, Math.min(i + chunkSize, text.length()));
            sendEvent(emitter, "token", Map.of("text", chunk));
            try {
                Thread.sleep(12);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String event, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize SSE event: {}", e.getMessage());
        } catch (IOException e) {
            log.debug("SSE client disconnected: {}", e.getMessage());
        }
    }
}
