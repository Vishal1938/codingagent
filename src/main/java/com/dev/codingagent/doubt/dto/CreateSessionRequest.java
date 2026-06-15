package com.dev.codingagent.doubt.dto;


/** Start a new chat session for a document. */
public record CreateSessionRequest(
        String documentId
) {}