package com.dev.codingagent.solver.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedQuestion(
        int id,
        String question,
        String type,
        Integer marks,
        String section,
        int foundOnPage
) {}