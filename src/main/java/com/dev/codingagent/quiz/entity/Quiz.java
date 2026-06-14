package com.dev.codingagent.quiz.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A quiz generated from an uploaded MCQ paper.
 * One-attempt model: NOT_ATTEMPTED → COMPLETED.
 */
@Document(collection = "quizzes")
public class Quiz {

    @Id
    private String id;

    @Indexed(unique = true)
    private String quizId;

    @Indexed
    private String userId;            // owner email

    private String title;
    private String subject;           // LLM-detected or user-set, optional
    private String sourceFileName;
    private Integer questionCount = 0;

    private String status = "NOT_ATTEMPTED";   // NOT_ATTEMPTED | COMPLETED

    private LocalDateTime createdAt = LocalDateTime.now();

    // Filled on submit (single attempt)
    private LocalDateTime completedAt;
    private Double  score;            // percentage 0-100
    private Integer correctCount;
    private Integer timeTakenSec;

    public Quiz() {}

    // ── Getters & setters ────────────────────────────────────────
    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getQuizId()                   { return quizId; }
    public void   setQuizId(String s)           { this.quizId = s; }
    public String getUserId()                   { return userId; }
    public void   setUserId(String s)           { this.userId = s; }
    public String getTitle()                    { return title; }
    public void   setTitle(String s)            { this.title = s; }
    public String getSubject()                  { return subject; }
    public void   setSubject(String s)          { this.subject = s; }
    public String getSourceFileName()           { return sourceFileName; }
    public void   setSourceFileName(String s)   { this.sourceFileName = s; }
    public Integer getQuestionCount()           { return questionCount; }
    public void    setQuestionCount(Integer c)  { this.questionCount = c; }
    public String getStatus()                   { return status; }
    public void   setStatus(String s)           { this.status = s; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getCompletedAt()       { return completedAt; }
    public void          setCompletedAt(LocalDateTime t) { this.completedAt = t; }
    public Double  getScore()                   { return score; }
    public void    setScore(Double s)           { this.score = s; }
    public Integer getCorrectCount()            { return correctCount; }
    public void    setCorrectCount(Integer c)   { this.correctCount = c; }
    public Integer getTimeTakenSec()            { return timeTakenSec; }
    public void    setTimeTakenSec(Integer t)   { this.timeTakenSec = t; }
}