package com.dev.codingagent.dto;

import com.dev.codingagent.entity.JobResult;
import java.time.LocalDateTime;

/**
 * Safe DTO returned to frontend — no server file paths exposed.
 */
public record JobResultDto(
        String        jobId,
        String        fileName,
        String        status,
        int           totalQuestions,
        long          processingTimeMs,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String        downloadUrl,    // relative URL — frontend uses this to download
        String        errorMessage
) {
    /** Build from entity — hides pdfPath, exposes only the download URL */
    public static JobResultDto from(JobResult r) {
        return new JobResultDto(
                r.getJobId(),
                r.getFileName(),
                r.getStatus(),
                r.getTotalQuestions(),
                r.getProcessingTimeMs(),
                r.getCreatedAt(),
                r.getCompletedAt(),
                "DONE".equals(r.getStatus())
                        ? "/api/solver/result/" + r.getJobId() + "/download"
                        : null,
                r.getErrorMessage()
        );
    }
}