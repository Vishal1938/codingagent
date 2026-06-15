package com.dev.codingagent.doubt.repository;

import com.dev.codingagent.doubt.entity.DocumentEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentEntity, String> {
    Optional<DocumentEntity> findByDocumentId(String documentId);
    Optional<DocumentEntity> findByDocumentIdAndUserId(String documentId, String userId);
    List<DocumentEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByDocumentId(String documentId);
}