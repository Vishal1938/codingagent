package com.dev.codingagent.papers.dto;

import com.dev.codingagent.papers.entity.Paper;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full paper view — metadata + all questions.
 * Returned by GET /api/papers/{paperId}.
 */
public record PaperDetailDto(
        String          paperId,
        String          title,
        String          board,
        String          classLevel,
        String          subject,
        Integer         year,
        Integer         totalMarks,
        Integer         durationMinutes,
        Integer         questionCount,
        Long            viewCount,
        Long            downloadCount,
        String          source,
        String          answerPdfUrl,
        LocalDateTime   createdAt,
        List<QuestionDto> questions
) {
    public static PaperDetailDto from(Paper p, List<QuestionDto> questions) {
        return new PaperDetailDto(
                p.getPaperId(),
                p.getTitle(),
                p.getBoard(),
                p.getClassLevel(),
                p.getSubject(),
                p.getYear(),
                p.getTotalMarks(),
                p.getDurationMinutes(),
                p.getQuestionCount(),
                p.getViewCount(),
                p.getDownloadCount(),
                p.getSource(),
                p.getAnswerPdfUrl(),
                p.getCreatedAt(),
                questions
        );
    }
}