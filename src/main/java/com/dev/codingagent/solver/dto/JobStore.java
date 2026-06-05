package com.dev.codingagent.solver.dto;


import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Component
public class JobStore {

    private final ConcurrentHashMap<String, SolverJob> jobs = new ConcurrentHashMap<>();

    public void save(SolverJob job) {
        jobs.put(job.getJobId(), job);
    }

    public Optional<SolverJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
