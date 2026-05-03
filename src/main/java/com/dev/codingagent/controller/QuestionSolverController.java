package com.dev.codingagent.controller;

import com.dev.codingagent.dto.JobStore;
import com.dev.codingagent.dto.QuestionSolverResponse;
import com.dev.codingagent.dto.SolverJob;
import com.dev.codingagent.service.AsyncQuestionSolverService;
import com.dev.codingagent.service.QuestionSolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/solver")
@CrossOrigin(origins = "http://localhost:3000")
public class QuestionSolverController {

    private static final Logger log = LoggerFactory.getLogger(QuestionSolverController.class);

    private final QuestionSolverService solverService;             // existing sync
    private final AsyncQuestionSolverService asyncSolverService;   // new async
    private final JobStore jobStore;

    public QuestionSolverController(QuestionSolverService solverService,
                                    AsyncQuestionSolverService asyncSolverService,
                                    JobStore jobStore) {
        this.solverService = solverService;
        this.asyncSolverService = asyncSolverService;
        this.jobStore = jobStore;
    }

    // ── EXISTING sync endpoint — unchanged ✅ ──────────────────
    @PostMapping(value = "/solve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QuestionSolverResponse> solve(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt) {
        log.info("📡  POST /api/solver/solve (sync)");
        try {
            return ResponseEntity.ok(solverService.solve(file, systemPrompt));
        } catch (Exception e) {
            log.error("❌  {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── NEW: async submit ──────────────────────────────────────
    @PostMapping(value = "/solve-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> solveAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt,
            @RequestParam(value = "email", required = false) String email) throws Exception {

        log.info("📡  POST /api/solver/solve-async");
        log.info("📄  File: {} | Email: {}", file.getOriginalFilename(), email);

        String jobId = UUID.randomUUID().toString();
        SolverJob job = new SolverJob(jobId, file.getOriginalFilename());
        jobStore.save(job);

        // Read bytes before multipart expires
        byte[] fileBytes = file.getBytes();

        // Fire and forget
        asyncSolverService.processAsync(job, fileBytes,
                file.getOriginalFilename(), systemPrompt, email);

        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "PENDING",
                "message", "Processing started. Poll /status/" + jobId + " to check progress.",
                "downloadUrl", "/api/solver/result/" + jobId + "/download"
        ));
    }

    // ── NEW: poll job status ───────────────────────────────────
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String jobId) {
        return jobStore.findById(jobId)
                .map(job -> ResponseEntity.ok(Map.of(
                        "jobId", job.getJobId(),
                        "status", job.getStatus().name(),
                        "fileName", job.getFileName() != null ? job.getFileName() : "",
                        "createdAt", job.getCreatedAt().toString(),
                        "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : "",
                        "downloadUrl", job.getStatus() == SolverJob.Status.DONE
                                ? "/api/solver/result/" + jobId + "/download" : "",
                        "error", job.getErrorMessage() != null ? job.getErrorMessage() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── NEW: download PDF ──────────────────────────────────────
    @GetMapping("/result/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        return jobStore.findById(jobId)
                .filter(job -> job.getStatus() == SolverJob.Status.DONE)
                .map(job -> {
                    File file = new File(job.getPdfPath());
                    Resource resource = new FileSystemResource(file);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"QA_Report_" + jobId + ".pdf\"")
                            .contentType(MediaType.APPLICATION_PDF)
                            .body(resource);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}