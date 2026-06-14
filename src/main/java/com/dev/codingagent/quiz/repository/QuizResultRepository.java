package com.dev.codingagent.quiz.repository;

import com.dev.codingagent.quiz.entity.QuizResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface QuizResultRepository extends MongoRepository<QuizResult, String> {
    Optional<QuizResult> findByQuizIdAndUserId(String quizId, String userId);
    void deleteByQuizId(String quizId);
}
