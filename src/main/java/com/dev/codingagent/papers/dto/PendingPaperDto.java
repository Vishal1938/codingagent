package com.dev.codingagent.papers.dto;

import com.dev.codingagent.papers.entity.Paper;
import java.time.LocalDateTime;

/**
 * Admin review queue item — a community-submitted paper awaiting approval.
 */
public record PendingPaperDto(
        String        paperId,
        String        title,
        String        board,
        String        classLevel,
        String        subject,
        Integer       year,
        Integer       questionCount,
        String        uploadedBy,        // who submitted it
        LocalDateTime createdAt
) {
    public static PendingPaperDto from(Paper p) {
        return new PendingPaperDto(
                p.getPaperId(),
                p.getTitle(),
                p.getBoard(),
                p.getClassLevel(),
                p.getSubject(),
                p.getYear(),
                p.getQuestionCount(),
                p.getUploadedBy(),
                p.getCreatedAt()
        );
    }
}