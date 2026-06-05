package com.dev.codingagent.solver.dto;

public record AgentRequest(
        String prompt,
        String filePath,
        String systemPrompt    // ✅ optional — overrides default role if provided
) {}