package com.dev.codingagent.doubt.dto;

import com.dev.codingagent.doubt.entity.DocumentEntity;
import java.time.LocalDateTime;

public record DocumentSummaryDto(
        String  documentId,
        String  title,
        String  sourceFileName,
        Integer chunkCount,
        String  status,
        LocalDateTime createdAt
) {
    public static DocumentSummaryDto from(DocumentEntity d) {
        return new DocumentSummaryDto(
                d.getDocumentId(), d.getTitle(), d.getSourceFileName(),
                d.getChunkCount(), d.getStatus(), d.getCreatedAt());
    }
}