package com.dev.codingagent.doubt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A document the user has uploaded to chat with (RAG source).
 * Named DocumentEntity to avoid clash with Spring's @Document annotation.
 */
@Document(collection = "doubt_documents")
public class DocumentEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String documentId;

    @Indexed
    private String userId;

    private String title;
    private String sourceFileName;
    private Integer chunkCount = 0;
    private String status = "PROCESSING";   // PROCESSING | READY | FAILED
    private String errorMessage;

    private LocalDateTime createdAt = LocalDateTime.now();

    public DocumentEntity() {}

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getDocumentId()               { return documentId; }
    public void   setDocumentId(String s)       { this.documentId = s; }
    public String getUserId()                   { return userId; }
    public void   setUserId(String s)           { this.userId = s; }
    public String getTitle()                    { return title; }
    public void   setTitle(String s)            { this.title = s; }
    public String getSourceFileName()           { return sourceFileName; }
    public void   setSourceFileName(String s)   { this.sourceFileName = s; }
    public Integer getChunkCount()              { return chunkCount; }
    public void    setChunkCount(Integer c)     { this.chunkCount = c; }
    public String getStatus()                   { return status; }
    public void   setStatus(String s)           { this.status = s; }
    public String getErrorMessage()             { return errorMessage; }
    public void   setErrorMessage(String s)     { this.errorMessage = s; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}