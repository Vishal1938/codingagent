package com.dev.codingagent.doubt.dto;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocJobStore {
    private final Map<String, DocJob> jobs = new ConcurrentHashMap<>();
    public void put(DocJob job)       { jobs.put(job.getJobId(), job); }
    public DocJob get(String jobId)   { return jobs.get(jobId); }
    public void remove(String jobId)  { jobs.remove(jobId); }
}