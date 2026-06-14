package com.dev.codingagent.quiz.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single attempt result for a quiz. Created on submit.
 * Feeds Analytics later (per-question correctness + topic).
 */
@Document(collection = "quiz_results")
public class QuizResult {

    @Id
    private String id;

    @Indexed
    private String quizId;

    @Indexed
    private String userId;

    private List<AnswerRecord> answers;
    private Double  score;            // percentage
    private Integer correctCount;
    private Integer totalQuestions;
    private Integer timeTakenSec;
    private LocalDateTime submittedAt = LocalDateTime.now();

    public QuizResult() {}

    /** One answer within the result. */
    public static class AnswerRecord {
        private String  quizQuestionId;
        private Integer selectedOption;   // null if skipped
        private Boolean isCorrect;
        private String  topic;            // denormalized for analytics

        public AnswerRecord() {}
        public AnswerRecord(String qId, Integer selected, Boolean correct, String topic) {
            this.quizQuestionId = qId;
            this.selectedOption = selected;
            this.isCorrect      = correct;
            this.topic          = topic;
        }

        public String  getQuizQuestionId()            { return quizQuestionId; }
        public void    setQuizQuestionId(String s)    { this.quizQuestionId = s; }
        public Integer getSelectedOption()            { return selectedOption; }
        public void    setSelectedOption(Integer i)   { this.selectedOption = i; }
        public Boolean getIsCorrect()                 { return isCorrect; }
        public void    setIsCorrect(Boolean b)        { this.isCorrect = b; }
        public String  getTopic()                     { return topic; }
        public void    setTopic(String s)             { this.topic = s; }
    }

    // ── Getters & setters ────────────────────────────────────────
    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getQuizId()                   { return quizId; }
    public void   setQuizId(String s)           { this.quizId = s; }
    public String getUserId()                   { return userId; }
    public void   setUserId(String s)           { this.userId = s; }
    public List<AnswerRecord> getAnswers()      { return answers; }
    public void   setAnswers(List<AnswerRecord> a) { this.answers = a; }
    public Double  getScore()                   { return score; }
    public void    setScore(Double s)           { this.score = s; }
    public Integer getCorrectCount()            { return correctCount; }
    public void    setCorrectCount(Integer c)   { this.correctCount = c; }
    public Integer getTotalQuestions()          { return totalQuestions; }
    public void    setTotalQuestions(Integer t) { this.totalQuestions = t; }
    public Integer getTimeTakenSec()            { return timeTakenSec; }
    public void    setTimeTakenSec(Integer t)   { this.timeTakenSec = t; }
    public LocalDateTime getSubmittedAt()       { return submittedAt; }
    public void          setSubmittedAt(LocalDateTime t) { this.submittedAt = t; }
}