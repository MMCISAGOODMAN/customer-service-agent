package com.example.customerservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ChatResponse {

    private String reply;
    private String sessionId;
    private String mode;
    private boolean fallback;
    private String errorDetail;
    private Integer reactRounds;
    private List<String> toolCalls;
    private Instant timestamp;

    public static ChatResponse success(String reply, String mode) {
        return ChatResponse.builder()
                .reply(reply)
                .mode(mode)
                .fallback(false)
                .timestamp(Instant.now())
                .build();
    }

    public static ChatResponse fallback(String reply, String errorDetail) {
        return ChatResponse.builder()
                .reply(reply)
                .mode("rule-based-fallback")
                .fallback(true)
                .errorDetail(errorDetail)
                .timestamp(Instant.now())
                .build();
    }

    public ChatResponse withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }
}
