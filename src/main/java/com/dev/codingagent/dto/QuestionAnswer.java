package com.dev.codingagent.dto;

public record QuestionAnswer(
        int id,
        String question,
        String answer,
        String type            // MCQ / short_answer / descriptive / numerical
) {}
