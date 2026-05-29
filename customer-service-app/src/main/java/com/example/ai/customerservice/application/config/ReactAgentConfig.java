package com.example.ai.customerservice.application.config;

import com.example.ai.agent.react.ReactAgentConfigurer;
import com.example.ai.customerservice.application.agent.CustomerServiceAgent;
import com.example.ai.customerservice.infrastructure.order.OrderTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ReactAgentConfig {

    private final ReactAgentConfigurer reactAgentConfigurer;

    @Bean
    CustomerServiceAgent customerServiceAgent(
            @Qualifier("chatModel") ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            OrderTools orderTools,
            @Value("${app.agent.max-sequential-tool-invocations:10}") int maxSequentialToolInvocations) {

        return reactAgentConfigurer.buildSyncAgent(
                CustomerServiceAgent.class, chatModel, chatMemoryProvider, orderTools, maxSequentialToolInvocations);
    }
}
