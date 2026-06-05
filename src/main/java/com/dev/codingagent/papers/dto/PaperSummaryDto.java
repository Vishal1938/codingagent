package com.dev.codingagent.papers.dto;

import com.dev.codingagent.papers.entity.Paper;

import java.time.LocalDateTime;

/**
 * Lightweight paper representation for grid/list views.
 * Doesn't include question content — fetch /papers/{id} for that.
 */
public record PaperSummaryDto(
        String paperId,
        String title,
        String board,
        String classLevel,
        String subject,
        Integer year,
        Integer questionCount,
        Long viewCount,
        Long downloadCount,
        String source,
        LocalDateTime createdAt
) {
    public static PaperSummaryDto from(Paper p) {
        return new PaperSummaryDto(
                p.getPaperId(),
                p.getTitle(),
                p.getBoard(),
                p.getClassLevel(),
                p.getSubject(),
                p.getYear(),
                p.getQuestionCount(),
                p.getViewCount(),
                p.getDownloadCount(),
                p.getSource(),
                p.getCreatedAt()
        );
    }
}