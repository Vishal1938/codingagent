package com.dev.codingagent.quiz.dto;

import java.util.List;

/**
 * Full result after submission — score, per-question review (with correct
 * answers + explanations), and topic-level analytics.
 */
public record QuizResultDto(
        String  quizId,
        String  title,
        Double  score,             // percentage
        Integer correctCount,
        Integer totalQuestions,
        Integer timeTakenSec,
        List<ReviewQuestion> questions,
        List<TopicBreakdown> topicBreakdown
) {
    public record ReviewQuestion(
            String  quizQuestionId,
            Integer questionNumber,
            String  text,
            List<String> options,
            Integer selectedOption,    // what the user chose (null = skipped)
            Integer correctOption,     // the right answer
            Boolean isCorrect,
            String  explanation,
            String  topic,
            Boolean answerConfident
    ) {}

    public record TopicBreakdown(
            String  topic,
            Integer correct,
            Integer total
    ) {}
}