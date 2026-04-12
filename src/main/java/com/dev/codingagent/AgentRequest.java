package com.dev.codingagent;

public record AgentRequest(
        String prompt,
        String filePath,
        String systemPrompt    // ✅ optional — overrides default role if provided
) {}