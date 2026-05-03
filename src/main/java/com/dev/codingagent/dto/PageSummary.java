package com.dev.codingagent.dto;

/**
 * Summary produced for one page of a PDF.
 *
 * @param pageNumber   1-based page index
 * @param title        short title the LLM inferred for this page (e.g. "Section B – Algebra")
 * @param summary      2-4 sentence plain-English summary of the page content
 * @param keyTopics    comma-separated key topics / concepts found on the page
 * @param pageType     what kind of content this page holds
 *                     (e.g. "cover", "instructions", "questions", "answer-key", "blank")
 * @param skipped      true when the page was blank and no LLM call was made
 */
public record PageSummary(
        int    pageNumber,
        String title,
        String summary,
        String keyTopics,
        String pageType,
        boolean skipped
) {

    /** Convenience factory for blank/skipped pages — no LLM output needed. */
    public static PageSummary skipped(int pageNumber) {
        return new PageSummary(pageNumber, null, null, null, "blank", true);
    }
}