package com.dev.codingagent.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Persisted job result — stored in MongoDB so users can
 * access their generated PDFs after logout/login.
 */
@Document(collection = "job_results")
public class JobResult {

    @Id
    private String id;

    @Indexed
    private String jobId;          // UUID — used in download URL

    @Indexed
    private String userEmail;      // owner — only this user can download

    private String fileName;       // original uploaded file name
    private String pdfPath;        // absolute path to generated PDF on server
    private long   processingTimeMs;
    private int    totalQuestions;
    private String status;         // DONE / FAILED
    private String errorMessage;
    private LocalDateTime createdAt  = LocalDateTime.now();
    private LocalDateTime completedAt;

    // ── Constructors ──────────────────────────────────────────

    public JobResult() {}

    public JobResult(String jobId, String userEmail, String fileName) {
        this.jobId      = jobId;
        this.userEmail  = userEmail;
        this.fileName   = fileName;
        this.status     = "PROCESSING";
        this.createdAt  = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────

    public String getId()                        { return id; }
    public String getJobId()                     { return jobId; }
    public String getUserEmail()                 { return userEmail; }
    public String getFileName()                  { return fileName; }
    public String getPdfPath()                   { return pdfPath; }
    public long   getProcessingTimeMs()          { return processingTimeMs; }
    public int    getTotalQuestions()            { return totalQuestions; }
    public String getStatus()                    { return status; }
    public String getErrorMessage()              { return errorMessage; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getCompletedAt()        { return completedAt; }

    public void setId(String id)                         { this.id = id; }
    public void setJobId(String jobId)                   { this.jobId = jobId; }
    public void setUserEmail(String userEmail)           { this.userEmail = userEmail; }
    public void setFileName(String fileName)             { this.fileName = fileName; }
    public void setPdfPath(String pdfPath)               { this.pdfPath = pdfPath; }
    public void setProcessingTimeMs(long t)              { this.processingTimeMs = t; }
    public void setTotalQuestions(int t)                 { this.totalQuestions = t; }
    public void setStatus(String status)                 { this.status = status; }
    public void setErrorMessage(String errorMessage)     { this.errorMessage = errorMessage; }
    public void setCreatedAt(LocalDateTime t)            { this.createdAt = t; }
    public void setCompletedAt(LocalDateTime t)          { this.completedAt = t; }

    // ── Convenience ───────────────────────────────────────────

    public void markDone(String pdfPath, long processingTimeMs, int totalQuestions) {
        this.pdfPath          = pdfPath;
        this.processingTimeMs = processingTimeMs;
        this.totalQuestions   = totalQuestions;
        this.status           = "DONE";
        this.completedAt      = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.errorMessage = error;
        this.status       = "FAILED";
        this.completedAt  = LocalDateTime.now();
    }
}