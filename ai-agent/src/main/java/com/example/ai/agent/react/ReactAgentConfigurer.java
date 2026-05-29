package com.example.ai.agent.react;

import com.example.ai.observability.ReactStepLogger;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReactAgentConfigurer {

    private final ReactStepLogger reactStepLogger;

    public <T> T buildSyncAgent(Class<T> agentClass, ChatModel chatModel,
                                 ChatMemoryProvider memoryProvider, Object tools,
                                 int maxSequentialToolInvocations) {
        return AiServices.builder(agentClass)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                .tools(tools)
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                .toolArgumentsErrorHandler(reactStepLogger::handleArgumentError)
                .toolExecutionErrorHandler(reactStepLogger::handleExecutionError)
                .hallucinatedToolNameStrategy(this::handleHallucinatedTool)
                .beforeToolExecution(reactStepLogger::logBeforeTool)
                .afterToolExecution(reactStepLogger::logAfterTool)
                .build();
    }

    public <T> T buildStreamingAgent(Class<T> agentClass, StreamingChatModel streamingChatModel,
                                    ChatMemoryProvider memoryProvider, Object tools,
                                    int maxSequentialToolInvocations) {
        return AiServices.builder(agentClass)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryProvider)
                .tools(tools)
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                .toolArgumentsErrorHandler(reactStepLogger::handleArgumentError)
                .toolExecutionErrorHandler(reactStepLogger::handleExecutionError)
                .hallucinatedToolNameStrategy(this::handleHallucinatedTool)
                .beforeToolExecution(reactStepLogger::logBeforeTool)
                .afterToolExecution(reactStepLogger::logAfterTool)
                .build();
    }

    private ToolExecutionResultMessage handleHallucinatedTool(dev.langchain4j.agent.tool.ToolExecutionRequest request) {
        reactStepLogger.logHallucinatedTool(request.name());
        return ToolExecutionResultMessage.from(
                request,
                "错误：工具 '" + request.name() + "' 不存在。请检查工具名后重新调用。");
    }
}
