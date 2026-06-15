package com.dev.codingagent.doubt.dto;


import com.dev.codingagent.doubt.entity.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageDto(
        String messageId,
        String role,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageDto from(ChatMessage m) {
        return new ChatMessageDto(
                m.getMessageId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}