package com.dev.codingagent.solver.repository;

import com.dev.codingagent.solver.entity.JobResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface JobResultRepository extends MongoRepository<JobResult, String> {

    // All jobs for a specific user — sorted by frontend
    List<JobResult> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    // Find one job — used for download (validates ownership)
    Optional<JobResult> findByJobIdAndUserEmail(String jobId, String userEmail);

    // Find by jobId only — used internally by async service
    Optional<JobResult> findByJobId(String jobId);
}