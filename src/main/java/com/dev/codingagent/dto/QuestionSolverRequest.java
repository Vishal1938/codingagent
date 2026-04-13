package com.dev.codingagent.dto;

public record QuestionSolverRequest(
        String systemPrompt    // optional — overrides default role
) {}