package com.dev.codingagent.papers.repository;


import com.dev.codingagent.papers.entity.Question;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends MongoRepository<Question, String> {

    Optional<Question> findByQuestionId(String questionId);

    List<Question> findByPaperId(String paperId);

    List<Question> findByPaperIdIn(List<String> paperIds);

    List<Question> findBySubjectAndTopic(String subject, String topic);

    // For Mock Test sampling later — by subject/board/class
    @Query("{ 'subject': ?0, 'board': ?1, 'class': ?2, 'isPublic': true }")
    List<Question> findForSampling(String subject, String board, String classLevel);

    long countByPaperId(String paperId);
}