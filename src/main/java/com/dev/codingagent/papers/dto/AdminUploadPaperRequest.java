package com.dev.codingagent.papers.dto;

/**
 * Metadata accompanying an admin paper upload.
 * The PDF file is sent as a separate multipart part.
 */
public record AdminUploadPaperRequest(
        String title,            // "CBSE Class 10 Mathematics 2024"
        String board,            // CBSE | ICSE | etc.
        String classLevel,       // 10
        String subject,          // Mathematics
        Integer year,            // 2024
        Integer totalMarks,      // optional
        Integer durationMinutes  // optional
) {}