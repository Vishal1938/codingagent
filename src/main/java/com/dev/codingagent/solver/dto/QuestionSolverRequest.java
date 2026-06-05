package com.dev.codingagent.solver.dto;

public record QuestionSolverRequest(
        String systemPrompt    // optional — overrides default role
) {}