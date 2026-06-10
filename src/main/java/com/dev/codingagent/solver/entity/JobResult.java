package com.dev.codingagent.solver.entity;


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
    private String publicUrl;

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

    // Community sharing (PR-3)
    private Boolean sharedToCommunity = false;   // user clicked "Share"
    private String  linkedPaperId;               // populated once shared → paper created

    public Boolean getSharedToCommunity()              { return sharedToCommunity; }
    public void    setSharedToCommunity(Boolean b)     { this.sharedToCommunity = b; }
    public String  getLinkedPaperId()                  { return linkedPaperId; }
    public void    setLinkedPaperId(String s)          { this.linkedPaperId = s; }

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

    public String getPublicUrl() {
        return publicUrl;
    }

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

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }
    // ── Convenience ───────────────────────────────────────────

    public void markDone(String pdfPath, long processingTimeMs, int totalQuestions,String publicUrl) {
        this.pdfPath          = pdfPath;
        this.processingTimeMs = processingTimeMs;
        this.totalQuestions   = totalQuestions;
        this.status           = "DONE";
        this.completedAt      = LocalDateTime.now();
        this.publicUrl=publicUrl;
    }

    public void markFailed(String error) {
        this.errorMessage = error;
        this.status       = "FAILED";
        this.completedAt  = LocalDateTime.now();
    }
}