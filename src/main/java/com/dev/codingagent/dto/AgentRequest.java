package com.dev.codingagent.dto;

public record AgentRequest(
        String prompt,
        String filePath,
        String systemPrompt    // ✅ optional — overrides default role if provided
) {}