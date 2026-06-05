package com.dev.codingagent.solver.dto;

import java.util.List;

/**
 * Response returned by POST /api/pdf/summarize.
 *
 * @param fileName        original file name from the upload
 * @param totalPages      total number of pages in the PDF
 * @param processedPages  pages that had content and were sent to the LLM
 * @param skippedPages    blank pages that were skipped
 * @param elapsedMs       total wall-clock time for the whole operation
 * @param pageSummaries   one {@link PageSummary} per page, in page order
 */
public record DocumentSummaryResponse(
        String            fileName,
        int               totalPages,
        int               processedPages,
        int               skippedPages,
        long              elapsedMs,
        List<PageSummary> pageSummaries
) {}