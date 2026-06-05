package com.dev.codingagent.solver.dto;

public record AgentResponse(
        String response,
        String workingDir,
        long responseTimeMs,
        int promptNumber,
        String fileName        // ✅ shows which file was processed
) {}