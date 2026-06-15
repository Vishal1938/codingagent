package com.dev.codingagent.doubt.repository;

import com.dev.codingagent.doubt.entity.DocumentChunk;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentChunkRepository extends MongoRepository<DocumentChunk, String> {
    List<DocumentChunk> findByDocumentId(String documentId);
    void deleteByDocumentId(String documentId);
    long countByDocumentId(String documentId);
}