package com.dev.codingagent.dto;


import java.time.LocalDateTime;

public class SolverJob {

    public enum Status { PENDING, PROCESSING, DONE, FAILED }

    private final String jobId;
    private Status status;
    private String fileName;
    private String pdfPath;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public SolverJob(String jobId, String fileName) {
        this.jobId = jobId;
        this.fileName = fileName;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public String getJobId()           { return jobId; }
    public Status getStatus()          { return status; }
    public String getFileName()        { return fileName; }
    public String getPdfPath()         { return pdfPath; }
    public String getErrorMessage()    { return errorMessage; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getCompletedAt(){ return completedAt; }

    public void markProcessing() { this.status = Status.PROCESSING; }

    public void markDone(String pdfPath) {
        this.status = Status.DONE;
        this.pdfPath = pdfPath;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.errorMessage = error;
        this.completedAt = LocalDateTime.now();
    }
}