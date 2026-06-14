package com.dev.codingagent.quiz.dto;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store for quiz-generation job status (polling). */
@Component
public class QuizJobStore {
    private final Map<String, QuizJob> jobs = new ConcurrentHashMap<>();
    public void put(QuizJob job)       { jobs.put(job.getJobId(), job); }
    public QuizJob get(String jobId)   { return jobs.get(jobId); }
    public void remove(String jobId)   { jobs.remove(jobId); }
}