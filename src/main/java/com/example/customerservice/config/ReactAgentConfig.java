package com.example.customerservice.config;

import com.example.customerservice.agent.CustomerServiceAgent;
import com.example.customerservice.order.OrderTools;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolErrorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ReactAgentConfig {

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
                // ReAct 循环上限：每次 LLM 返回工具调用计为 1 轮（Thought → Action → Observation）
                .maxSequentialToolsInvocations(maxSequentialToolInvocations)
                // 参数错误：反馈给 LLM，让其修正后重试（ReAct Observation）
                .toolArgumentsErrorHandler(ReactAgentConfig::handleArgumentError)
                // 工具执行错误：反馈结构化提示，引导 LLM 调整策略
                .toolExecutionErrorHandler(ReactAgentConfig::handleExecutionError)
                // 幻觉工具名：提示 LLM 使用正确工具名重试
                .hallucinatedToolNameStrategy(request -> ToolExecutionResultMessage.from(
                        request,
                        "错误：工具 '" + request.name() + "' 不存在。"
                                + "可用工具：queryOrderById、queryOrdersByPhone、queryOrderByIdAndPhone。"
                                + "请修正后重新调用。"))
                .beforeToolExecution(execution -> log.info(
                        "[ReAct Action] tool={}, args={}",
                        execution.request().name(),
                        execution.request().arguments()))
                .afterToolExecution(execution -> log.info(
                        "[ReAct Observation] tool={}, result={}",
                        execution.request().name(),
                        truncate(execution.result(), 200)))
                .build();
    }

    private static ToolErrorHandlerResult handleArgumentError(Throwable error, ToolErrorContext context) {
        String message = """
                [工具参数错误] %s
                请检查参数格式后重新调用工具。订单号示例：ORD-10001；手机号：11位数字。
                """.formatted(error.getMessage());
        log.warn("[ReAct] argument error on tool {}: {}", context.toolExecutionRequest().name(), error.getMessage());
        return ToolErrorHandlerResult.text(message);
    }

    private static ToolErrorHandlerResult handleExecutionError(Throwable error, ToolErrorContext context) {
        String message = """
                [工具执行失败] %s
                请根据以上 Observation 调整策略：修正参数、换用其他查询工具，或向用户追问缺失信息。
                """.formatted(sanitize(error.getMessage()));
        log.warn("[ReAct] execution error on tool {}: {}", context.toolExecutionRequest().name(), error.getMessage());
        return ToolErrorHandlerResult.text(message);
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
