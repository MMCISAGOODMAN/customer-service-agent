package com.example.customerservice.observability;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次对话的 ReAct 追踪上下文（线程内有效）。
 */
@Getter
public class ReactTraceContext {

    private static final ThreadLocal<ReactTraceContext> HOLDER = new ThreadLocal<>();

    private final String traceId;
    private final String sessionId;
    private final String userMessage;
    private final Instant startedAt;
    private final AtomicInteger llmRound = new AtomicInteger(0);
    private final AtomicInteger toolStep = new AtomicInteger(0);

    private ReactTraceContext(String sessionId, String userMessage) {
        this.traceId = UUID.randomUUID().toString().substring(0, 8);
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.startedAt = Instant.now();
    }

    public static void start(String sessionId, String userMessage) {
        HOLDER.set(new ReactTraceContext(sessionId, userMessage));
    }

    public static ReactTraceContext current() {
        return HOLDER.get();
    }

    public static boolean isActive() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public int nextLlmRound() {
        return llmRound.incrementAndGet();
    }

    public int currentLlmRound() {
        return llmRound.get();
    }

    public int nextToolStep() {
        return toolStep.incrementAndGet();
    }

    public int toolStepCount() {
        return toolStep.get();
    }

    public long elapsedMs() {
        return Instant.now().toEpochMilli() - startedAt.toEpochMilli();
    }
}
