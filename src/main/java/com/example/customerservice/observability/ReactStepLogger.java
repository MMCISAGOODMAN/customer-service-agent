package com.example.customerservice.observability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReactStepLogger {

    private static final String SEP = "════════════════════════════════════════════════════════";

    @Value("${app.agent.trace-enabled:true}")
    private boolean traceEnabled;

    @Value("${app.agent.trace-max-result-length:800}")
    private int maxResultLength;

    public void startTrace(String sessionId, String userMessage) {
        if (!traceEnabled) {
            return;
        }
        ReactTraceContext.start(sessionId, userMessage);
        ReactTraceContext ctx = ReactTraceContext.current();
        log.info("""
                \n{}
                [ReAct TRACE START] traceId={} sessionId={}
                [ReAct USER] {}
                {}
                """, SEP, ctx.getTraceId(), sessionId, userMessage, SEP);
    }

    public void logLlmRequest(int round) {
        if (!traceEnabled || !ReactTraceContext.isActive()) {
            return;
        }
        ReactTraceContext ctx = ReactTraceContext.current();
        log.info("""
                \n{} Round {} ── THOUGHT (调用 LLM 推理) ── traceId={} sessionId={}
                """, SEP, round, ctx.getTraceId(), ctx.getSessionId());
    }

    public void logLlmResponse(int round, String thinkingText, List<ToolExecutionRequest> toolCalls, boolean finalAnswer) {
        if (!traceEnabled || !ReactTraceContext.isActive()) {
            return;
        }
        ReactTraceContext ctx = ReactTraceContext.current();
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(SEP).append(" Round ").append(round);
        if (finalAnswer) {
            sb.append(" ── FINAL ANSWER (最终回复) ──\n");
        } else {
            sb.append(" ── THOUGHT RESULT (推理结果) ──\n");
        }
        if (thinkingText != null && !thinkingText.isBlank()) {
            sb.append("  [LLM 文本] ").append(truncate(thinkingText)).append("\n");
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            sb.append("  [计划 Action] 共 ").append(toolCalls.size()).append(" 个工具调用:\n");
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolExecutionRequest req = toolCalls.get(i);
                sb.append("    ").append(i + 1).append(". ")
                        .append(req.name()).append("(").append(req.arguments()).append(")\n");
            }
        } else if (!finalAnswer) {
            sb.append("  [计划 Action] 无工具调用\n");
        }
        sb.append(SEP);
        log.info(sb.toString());
    }

    public void logBeforeTool(BeforeToolExecution execution) {
        if (!traceEnabled) {
            return;
        }
        ReactTraceContext ctx = ReactTraceContext.current();
        int round = ctx != null ? ctx.currentLlmRound() : 0;
        int step = ctx != null ? ctx.nextToolStep() : 0;
        Object memoryId = execution.invocationContext() != null
                ? execution.invocationContext().chatMemoryId()
                : "unknown";

        log.info("""
                \n── Round {} Step {} ── ACTION (执行工具) ── sessionId={}
                  tool={}
                  args={}
                """,
                round, step, memoryId,
                execution.request().name(),
                execution.request().arguments());
    }

    public void logAfterTool(ToolExecution execution) {
        if (!traceEnabled) {
            return;
        }
        ReactTraceContext ctx = ReactTraceContext.current();
        int round = ctx != null ? ctx.currentLlmRound() : 0;
        String status = execution.hasFailed() ? "FAILED" : "SUCCESS";
        String duration = execution.duration() != null ? execution.duration().toMillis() + "ms" : "n/a";

        log.info("""
                ── Round {} ── OBSERVATION (工具返回) [{}] duration={}
                  tool={}
                  result={}
                """,
                round, status, duration,
                execution.request().name(),
                truncate(execution.result()));
    }

    public ToolErrorHandlerResult handleArgumentError(Throwable error, ToolErrorContext context) {
        log.warn("""
                ── OBSERVATION (参数错误，将反馈给 LLM 重试) ──
                  tool={}
                  error={}
                """,
                context.toolExecutionRequest().name(), error.getMessage());

        String message = """
                [工具参数错误] %s
                请检查参数格式后重新调用工具。订单号示例：ORD-10001；手机号：11位数字。
                """.formatted(error.getMessage());
        return ToolErrorHandlerResult.text(message);
    }

    public ToolErrorHandlerResult handleExecutionError(Throwable error, ToolErrorContext context) {
        log.warn("""
                ── OBSERVATION (执行失败，将反馈给 LLM 调整策略) ──
                  tool={}
                  error={}
                """,
                context.toolExecutionRequest().name(), error.getMessage());

        String message = """
                [工具执行失败] %s
                请根据以上 Observation 调整策略：修正参数、换用其他查询工具，或向用户追问缺失信息。
                """.formatted(error.getMessage() != null ? error.getMessage() : "未知错误");
        return ToolErrorHandlerResult.text(message);
    }

    public void logHallucinatedTool(String toolName) {
        log.warn("""
                ── OBSERVATION (工具名幻觉) ──
                  模型请求了不存在的工具: {}
                  已提示可用工具列表，等待 LLM 重试
                """, toolName);
    }

    public void finishTrace(String finalReply, int toolInvocations) {
        if (!traceEnabled) {
            ReactTraceContext.clear();
            return;
        }
        ReactTraceContext ctx = ReactTraceContext.current();
        if (ctx == null) {
            return;
        }
        log.info("""
                \n{}
                [ReAct TRACE END] traceId={} sessionId={}
                  llmRounds={}
                  toolSteps={}
                  toolInvocations={}
                  elapsed={}ms
                  finalReply={}
                {}
                """,
                SEP,
                ctx.getTraceId(),
                ctx.getSessionId(),
                ctx.currentLlmRound(),
                ctx.toolStepCount(),
                toolInvocations,
                ctx.elapsedMs(),
                truncate(finalReply),
                SEP);
        ReactTraceContext.clear();
    }

    public void logLlmError(Throwable error) {
        if (!traceEnabled) {
            return;
        }
        ReactTraceContext ctx = ReactTraceContext.current();
        log.error("[ReAct LLM ERROR] traceId={} sessionId={} error={}",
                ctx != null ? ctx.getTraceId() : "n/a",
                ctx != null ? ctx.getSessionId() : "n/a",
                error.getMessage());
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace("\n", " ").replace("\r", " ").trim();
        if (oneLine.length() <= maxResultLength) {
            return oneLine;
        }
        return oneLine.substring(0, maxResultLength) + "...";
    }
}
