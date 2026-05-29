package com.example.customerservice.config;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean("chatModel")
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai", matchIfMissing = true)
    ChatModel openAiChatModel(@Qualifier("openAiChatModel") ChatModel model) {
        return model;
    }

    @Bean("chatModel")
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    ChatModel ollamaChatModel(@Qualifier("ollamaChatModel") ChatModel model) {
        return model;
    }
}
