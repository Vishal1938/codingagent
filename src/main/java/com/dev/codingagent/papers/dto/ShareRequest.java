package com.dev.codingagent.papers.dto;


/**
 * Metadata the user provides when sharing one of their reports
 * to the community archive. They confirm/correct the auto-detected
 * board/class/subject/year since their original upload didn't capture these.
 */
public record ShareRequest(
        String  board,        // CBSE | ICSE | etc.
        String  classLevel,   // 10
        String  subject,      // Mathematics
        Integer year          // 2024
) {}