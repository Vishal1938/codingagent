package com.dev.codingagent.quiz.dto;

import com.dev.codingagent.quiz.entity.Quiz;
import java.time.LocalDateTime;

/** Archive list item. */
public record QuizSummaryDto(
        String  quizId,
        String  title,
        String  subject,
        Integer questionCount,
        String  status,            // NOT_ATTEMPTED | COMPLETED
        Double  score,             // null if not attempted
        Integer correctCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static QuizSummaryDto from(Quiz q) {
        return new QuizSummaryDto(
                q.getQuizId(), q.getTitle(), q.getSubject(),
                q.getQuestionCount(), q.getStatus(),
                q.getScore(), q.getCorrectCount(),
                q.getCreatedAt(), q.getCompletedAt()
        );
    }
}