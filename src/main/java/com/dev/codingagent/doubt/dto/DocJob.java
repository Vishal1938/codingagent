package com.dev.codingagent.doubt.dto;


import java.time.LocalDateTime;

/** In-memory status for an async document-ingestion job. */
public class DocJob {
    private String jobId;
    private String documentId;
    private String status;            // PENDING | PROCESSING | DONE | FAILED
    private String fileName;
    private String error;
    private Integer chunkCount;
    private LocalDateTime createdAt = LocalDateTime.now();

    public DocJob() {}
    public DocJob(String jobId, String fileName) {
        this.jobId = jobId; this.fileName = fileName; this.status = "PENDING";
    }

    public String getJobId()                  { return jobId; }
    public void   setJobId(String s)          { this.jobId = s; }
    public String getDocumentId()             { return documentId; }
    public void   setDocumentId(String s)     { this.documentId = s; }
    public String getStatus()                 { return status; }
    public void   setStatus(String s)         { this.status = s; }
    public String getFileName()               { return fileName; }
    public void   setFileName(String s)       { this.fileName = s; }
    public String getError()                  { return error; }
    public void   setError(String s)          { this.error = s; }
    public Integer getChunkCount()            { return chunkCount; }
    public void    setChunkCount(Integer c)   { this.chunkCount = c; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}