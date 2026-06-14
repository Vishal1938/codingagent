package com.dev.codingagent.quiz.dto;

import java.time.LocalDateTime;

/** In-memory status for an async quiz-generation job. */
public class QuizJob {
    private String jobId;
    private String quizId;            // populated once created
    private String status;            // PENDING | PROCESSING | DONE | FAILED
    private String fileName;
    private String error;
    private Integer questionCount;
    private LocalDateTime createdAt = LocalDateTime.now();

    public QuizJob() {}
    public QuizJob(String jobId, String fileName) {
        this.jobId = jobId;
        this.fileName = fileName;
        this.status = "PENDING";
    }

    public String getJobId()                  { return jobId; }
    public void   setJobId(String s)          { this.jobId = s; }
    public String getQuizId()                 { return quizId; }
    public void   setQuizId(String s)         { this.quizId = s; }
    public String getStatus()                 { return status; }
    public void   setStatus(String s)         { this.status = s; }
    public String getFileName()               { return fileName; }
    public void   setFileName(String s)       { this.fileName = s; }
    public String getError()                  { return error; }
    public void   setError(String s)          { this.error = s; }
    public Integer getQuestionCount()         { return questionCount; }
    public void    setQuestionCount(Integer c){ this.questionCount = c; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}