package com.example.ai.llm.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreamingLlmConfig {

    @Bean("streamingChatModel")
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai", matchIfMissing = true)
    StreamingChatModel openAiStreamingChatModel(@Qualifier("openAiStreamingChatModel") StreamingChatModel model) {
        return model;
    }

    @Bean("streamingChatModel")
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    StreamingChatModel ollamaStreamingChatModel(@Qualifier("ollamaStreamingChatModel") StreamingChatModel model) {
        return model;
    }
}
