package com.example.customerservice.config;

import com.example.customerservice.agent.CustomerServiceAgent;
import com.example.customerservice.observability.ReactStepLogger;
import com.example.customerservice.order.OrderTools;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ReactAgentConfig {

    private final ReactStepLogger reactStepLogger;

    @Bean
    CustomerServiceAgent customerServiceAgent(
            @Qualifier("chatModel") ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            OrderTools orderTools,
            @Value("${app.agent.max-sequential-tool-invocations:10}") int maxSequentialToolInvocations) {

        return AiServices.builder(CustomerServiceAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(orderTools)
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                .toolArgumentsErrorHandler(reactStepLogger::handleArgumentError)
                .toolExecutionErrorHandler(reactStepLogger::handleExecutionError)
                .hallucinatedToolNameStrategy(request -> {
                    reactStepLogger.logHallucinatedTool(request.name());
                    return ToolExecutionResultMessage.from(
                            request,
                            "错误：工具 '" + request.name() + "' 不存在。"
                                    + "可用工具：queryOrderById、queryOrdersByPhone、queryOrderByIdAndPhone。"
                                    + "请修正后重新调用。");
                })
                .beforeToolExecution(reactStepLogger::logBeforeTool)
                .afterToolExecution(reactStepLogger::logAfterTool)
                .build();
    }
}
