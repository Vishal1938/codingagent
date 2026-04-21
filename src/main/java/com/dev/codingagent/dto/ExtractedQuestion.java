package com.dev.codingagent.dto;


public record ExtractedQuestion(
        int id,
        String question,
        String type,
        Integer marks,
        String section,
        int foundOnPage
) {}