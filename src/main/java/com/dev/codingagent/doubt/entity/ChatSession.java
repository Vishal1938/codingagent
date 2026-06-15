package com.dev.codingagent.doubt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A chat conversation tied to one document.
 */
@Document(collection = "doubt_chat_sessions")
public class ChatSession {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sessionId;

    @Indexed
    private String userId;

    @Indexed
    private String documentId;

    private String title;             // e.g. first question, or "New chat"
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    public ChatSession() {}

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getSessionId()                { return sessionId; }
    public void   setSessionId(String s)        { this.sessionId = s; }
    public String getUserId()                   { return userId; }
    public void   setUserId(String s)           { this.userId = s; }
    public String getDocumentId()               { return documentId; }
    public void   setDocumentId(String s)       { this.documentId = s; }
    public String getTitle()                    { return title; }
    public void   setTitle(String s)            { this.title = s; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getLastMessageAt()     { return lastMessageAt; }
    public void          setLastMessageAt(LocalDateTime t) { this.lastMessageAt = t; }
}