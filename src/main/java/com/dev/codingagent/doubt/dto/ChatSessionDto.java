package com.dev.codingagent.doubt.dto;

import com.dev.codingagent.doubt.entity.ChatSession;
import java.time.LocalDateTime;

public record ChatSessionDto(
        String sessionId,
        String documentId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt
) {
    public static ChatSessionDto from(ChatSession s) {
        return new ChatSessionDto(
                s.getSessionId(), s.getDocumentId(), s.getTitle(),
                s.getCreatedAt(), s.getLastMessageAt());
    }
}