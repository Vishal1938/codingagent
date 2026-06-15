package com.dev.codingagent.doubt.dto;

/** A user sending a message in a session. */
public record SendMessageRequest(
        String sessionId,
        String message
) {}