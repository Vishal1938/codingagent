package com.dev.codingagent.quiz.repository;

import com.dev.codingagent.quiz.entity.Quiz;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends MongoRepository<Quiz, String> {
    Optional<Quiz> findByQuizId(String quizId);
    Optional<Quiz> findByQuizIdAndUserId(String quizId, String userId);
    List<Quiz> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByQuizId(String quizId);
}