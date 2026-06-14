package com.dev.codingagent.quiz.dto;

import com.dev.codingagent.quiz.entity.Quiz;
import com.dev.codingagent.quiz.entity.QuizQuestion;
import java.util.List;

/**
 * Quiz shape for ATTEMPTING — deliberately omits correctOption and explanation
 * so a user can't inspect network traffic to cheat.
 */
public record QuizAttemptDto(
        String quizId,
        String title,
        String subject,
        Integer questionCount,
        List<AttemptQuestion> questions
) {
    public record AttemptQuestion(
            String quizQuestionId,
            Integer questionNumber,
            String text,
            List<String> options
            // NOTE: no correctOption, no explanation — intentionally hidden
    ) {}

    public static QuizAttemptDto from(Quiz quiz, List<QuizQuestion> questions) {
        List<AttemptQuestion> aq = questions.stream()
                .map(q -> new AttemptQuestion(
                        q.getQuizQuestionId(),
                        q.getQuestionNumber(),
                        q.getText(),
                        q.getOptions()))
                .toList();
        return new QuizAttemptDto(
                quiz.getQuizId(), quiz.getTitle(), quiz.getSubject(),
                quiz.getQuestionCount(), aq);
    }
}