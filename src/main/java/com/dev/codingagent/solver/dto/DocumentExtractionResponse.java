package com.dev.codingagent.solver.dto;


import java.util.List;

public record DocumentExtractionResponse(
        String fileName,
        int totalPages,
        int pagesWithQuestions,
        int pagesSkipped,
        int totalQuestionsFound,
        long processingTimeMs,
        List<PageExtractionResult> pageBreakdown,
        List<ExtractedQuestion> allQuestions
) {}