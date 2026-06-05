package com.dev.codingagent.papers.dto;
import java.util.List;

/**
 * Paginated search response for the browse grid.
 */
public record PaperSearchResponse(
        List<PaperSummaryDto> papers,
        int    page,
        int    pageSize,
        long   totalElements,
        int    totalPages,
        boolean hasMore
) {}