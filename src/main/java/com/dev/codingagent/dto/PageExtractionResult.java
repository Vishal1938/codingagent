package com.dev.codingagent.dto;


import java.util.List;

public record PageExtractionResult(
        int pageNumber,
        boolean hasQuestions,
        String skipReason,
        List<ExtractedQuestion> questions
) {}