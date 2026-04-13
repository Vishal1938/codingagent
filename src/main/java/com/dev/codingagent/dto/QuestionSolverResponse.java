package com.dev.codingagent.dto;

import java.util.List;

public record QuestionSolverResponse(
        String fileName,
        int totalQuestions,
        long processingTimeMs,
        List<QuestionAnswer> questions
) {}
