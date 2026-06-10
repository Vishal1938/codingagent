package com.dev.codingagent.solver.dto;

import com.dev.codingagent.solver.entity.JobResult;
import java.time.LocalDateTime;

/**
 * Safe DTO returned to frontend — no server file paths exposed.
 */
public record JobResultDto(
        String jobId, String fileName, String status,
        int totalQuestions, long processingTimeMs,
        LocalDateTime createdAt, LocalDateTime completedAt,
        String downloadUrl, String errorMessage,
        Boolean sharedToCommunity,    //
        String  linkedPaperId         //
) {
    public static JobResultDto from(JobResult r) {
        return new JobResultDto(
                r.getJobId(), r.getFileName(), r.getStatus(),
                r.getTotalQuestions(), r.getProcessingTimeMs(),
                r.getCreatedAt(), r.getCompletedAt(),
                "DONE".equals(r.getStatus())
                        ? "/api/solver/result/" + r.getJobId() + "/download" : null,
                r.getErrorMessage(),
                r.getSharedToCommunity() != null && r.getSharedToCommunity(),  // ← add
                r.getLinkedPaperId()                                            // ← add
        );
    }
}