package com.dev.codingagent.doubt.repository;

import com.dev.codingagent.doubt.entity.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
    Optional<ChatSession> findBySessionId(String sessionId);
    Optional<ChatSession> findBySessionIdAndUserId(String sessionId, String userId);
    List<ChatSession> findByDocumentIdAndUserIdOrderByLastMessageAtDesc(String documentId, String userId);
    void deleteByDocumentId(String documentId);
}