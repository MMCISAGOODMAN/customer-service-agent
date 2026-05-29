package com.example.customerservice.observability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 监听每次 LLM 调用，输出 ReAct 的 THOUGHT 阶段日志。
 * LangChain4j Spring Boot Starter 会自动注册所有 ChatModelListener Bean。
 */
@Component
@RequiredArgsConstructor
public class ReactChatModelListener implements ChatModelListener {

    private final ReactStepLogger stepLogger;

    @Value("${app.agent.trace-enabled:true}")
    private boolean traceEnabled;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        if (!traceEnabled || !ReactTraceContext.isActive()) {
            return;
        }
        int round = ReactTraceContext.current().nextLlmRound();
        stepLogger.logLlmRequest(round);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        if (!traceEnabled || !ReactTraceContext.isActive()) {
            return;
        }
        int round = ReactTraceContext.current().currentLlmRound();
        ChatResponse chatResponse = responseContext.chatResponse();
        if (chatResponse == null || chatResponse.aiMessage() == null) {
            return;
        }
        AiMessage aiMessage = chatResponse.aiMessage();
        String text = aiMessage.text();
        List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();
        boolean hasTools = toolCalls != null && !toolCalls.isEmpty();
        boolean finalAnswer = !hasTools && text != null && !text.isBlank();

        stepLogger.logLlmResponse(round, text, toolCalls, finalAnswer);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        stepLogger.logLlmError(errorContext.error());
    }
}
