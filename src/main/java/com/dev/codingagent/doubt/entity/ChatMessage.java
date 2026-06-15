package com.dev.codingagent.doubt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A single message in a chat session. role = "user" | "assistant".
 */
@Document(collection = "doubt_chat_messages")
public class ChatMessage {

    @Id
    private String id;

    private String messageId;

    @Indexed
    private String sessionId;

    private String role;              // user | assistant
    private String content;
    private List<String> citedChunkIds;   // for assistant messages — which chunks grounded it
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatMessage() {}

    public ChatMessage(String messageId, String sessionId, String role, String content) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getMessageId()                { return messageId; }
    public void   setMessageId(String s)        { this.messageId = s; }
    public String getSessionId()                { return sessionId; }
    public void   setSessionId(String s)        { this.sessionId = s; }
    public String getRole()                     { return role; }
    public void   setRole(String s)             { this.role = s; }
    public String getContent()                  { return content; }
    public void   setContent(String s)          { this.content = s; }
    public List<String> getCitedChunkIds()      { return citedChunkIds; }
    public void         setCitedChunkIds(List<String> c) { this.citedChunkIds = c; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}