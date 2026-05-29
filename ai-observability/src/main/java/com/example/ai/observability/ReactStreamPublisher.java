package com.example.ai.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 将 ReAct 步骤桥接到 SSE 流（ThreadLocal，仅流式会话期间有效）。
 */
public final class ReactStreamPublisher {

    private static final ThreadLocal<Consumer<Map<String, Object>>> SINK = new ThreadLocal<>();

    private ReactStreamPublisher() {
    }

    public static void bind(Consumer<Map<String, Object>> consumer) {
        SINK.set(consumer);
    }

    public static void clear() {
        SINK.remove();
    }

    public static void publish(String phase, Map<String, Object> fields) {
        Consumer<Map<String, Object>> sink = SINK.get();
        if (sink == null) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("phase", phase);
        event.putAll(fields);
        sink.accept(event);
    }
}
