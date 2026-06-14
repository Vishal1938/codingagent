package com.dev.codingagent.quiz.repository;

import com.dev.codingagent.quiz.entity.QuizQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuizQuestionRepository extends MongoRepository<QuizQuestion, String> {
    List<QuizQuestion> findByQuizIdOrderByQuestionNumberAsc(String quizId);
    void deleteByQuizId(String quizId);
    long countByQuizId(String quizId);
}