package com.dev.codingagent.papers.dto;


import java.util.List;

/**
 * Available filter values for the browse UI dropdowns.
 * Computed from the current set of public approved papers.
 */
public record FilterOptionsDto(
        List<String>  boards,
        List<String>  classes,
        List<String>  subjects,
        List<Integer> years
) {}