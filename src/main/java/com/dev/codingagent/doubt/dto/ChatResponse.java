package com.dev.codingagent.doubt.dto;

/** The assistant's reply to a message. */
public record ChatResponse(
        String sessionId,
        String answer,
        boolean grounded     // true if relevant chunks were found
) {}