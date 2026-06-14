package com.dev.codingagent.quiz.dto;

import java.util.List;

/**
 * User's submitted answers for a quiz.
 * selectedOption may be null for skipped questions.
 */
public record QuizSubmitRequest(
        List<SubmittedAnswer> answers,
        Integer timeTakenSec
) {
    public record SubmittedAnswer(
            String  quizQuestionId,
            Integer selectedOption
    ) {}
}