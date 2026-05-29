package com.example.ai.customerservice.application.config;

import com.example.ai.agent.react.ReactAgentConfigurer;
import com.example.ai.customerservice.application.agent.CustomerServiceStreamingAgent;
import com.example.ai.customerservice.infrastructure.order.OrderTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class StreamingAgentConfig {

    private final ReactAgentConfigurer reactAgentConfigurer;

    @Bean
    CustomerServiceStreamingAgent customerServiceStreamingAgent(
            @Qualifier("streamingChatModel") StreamingChatModel streamingChatModel,
            ChatMemoryProvider chatMemoryProvider,
            OrderTools orderTools,
            @Value("${app.agent.max-sequential-tool-invocations:10}") int maxSequentialToolInvocations) {

        return reactAgentConfigurer.buildStreamingAgent(
                CustomerServiceStreamingAgent.class, streamingChatModel, chatMemoryProvider, orderTools, maxSequentialToolInvocations);
    }
}
