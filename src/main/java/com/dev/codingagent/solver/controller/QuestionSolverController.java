package com.dev.codingagent.solver.controller;

import com.dev.codingagent.solver.dto.JobResultDto;
import com.dev.codingagent.solver.dto.JobStore;
import com.dev.codingagent.solver.dto.QuestionSolverResponse;
import com.dev.codingagent.solver.dto.SolverJob;
import com.dev.codingagent.solver.repository.JobResultRepository;
import com.dev.codingagent.solver.service.AsyncQuestionSolverService;
import com.dev.codingagent.solver.service.QuestionSolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/solver")
@CrossOrigin(origins = "http://localhost:3000")
public class QuestionSolverController {

    private static final Logger log = LoggerFactory.getLogger(QuestionSolverController.class);

    private final QuestionSolverService      solverService;
    private final AsyncQuestionSolverService asyncSolverService;
    private final JobStore                   jobStore;
    private final JobResultRepository        jobResultRepository;  // ← NEW

    public QuestionSolverController(QuestionSolverService solverService,
                                    AsyncQuestionSolverService asyncSolverService,
                                    JobStore jobStore,
                                    JobResultRepository jobResultRepository) {
        this.solverService       = solverService;
        this.asyncSolverService  = asyncSolverService;
        this.jobStore            = jobStore;
        this.jobResultRepository = jobResultRepository;
    }

    // ── Sync solve — unchanged ─────────────────────────────────────────────

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

    // ── Async submit ───────────────────────────────────────────────────────

    @PostMapping(value = "/solve-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> solveAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt,
            @RequestParam(value = "email", required = false) String email,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        // Get logged-in user's email from JWT — no extra param needed from frontend
        String userEmail = userDetails.getUsername();
        log.info("📡  POST /api/solver/solve-async | user: {} | file: {}",
                userEmail, file.getOriginalFilename());

        String jobId = UUID.randomUUID().toString();
        SolverJob job = new SolverJob(jobId, file.getOriginalFilename());
        jobStore.save(job);

        byte[] fileBytes = file.getBytes();

        // Pass userEmail so async service persists result against this user
        asyncSolverService.processAsync(job, fileBytes,
                file.getOriginalFilename(), systemPrompt, email, userEmail);

        return ResponseEntity.accepted().body(Map.of(
                "jobId",       jobId,
                "status",      "PENDING",
                "message",     "Processing started. Poll /status/" + jobId,
                "downloadUrl", "/api/solver/result/" + jobId + "/download"
        ));
    }

    // ── Poll job status ────────────────────────────────────────────────────

    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String jobId) {
        return jobStore.findById(jobId)
                .map(job -> ResponseEntity.ok(Map.of(
                        "jobId",       job.getJobId(),
                        "status",      job.getStatus().name(),
                        "fileName",    job.getFileName() != null ? job.getFileName() : "",
                        "createdAt",   job.getCreatedAt().toString(),
                        "completedAt", job.getCompletedAt() != null
                                ? job.getCompletedAt().toString() : "",
                        "downloadUrl", job.getStatus() == SolverJob.Status.DONE
                                ? "/api/solver/result/" + jobId + "/download" : "",
                        "error",       job.getErrorMessage() != null
                                ? job.getErrorMessage() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Job history for logged-in user ─────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<List<JobResultDto>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userEmail = userDetails.getUsername();
        log.info("📡  GET /api/solver/history | user: {}", userEmail);

        List<JobResultDto> history = jobResultRepository
                .findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(JobResultDto::from)
                .toList();

        return ResponseEntity.ok(history);
    }

    // ── Download PDF — ownership validated ────────────────────────────────
    @GetMapping("/result/{jobId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userEmail = userDetails.getUsername();
        log.info("📡  GET /api/solver/result/{}/download | user: {}", jobId, userEmail);

        return (ResponseEntity<Resource>) jobResultRepository
                .findByJobIdAndUserEmail(jobId, userEmail)
                .filter(r -> "DONE".equals(r.getStatus()))
                .map(r -> {

                    // ── Option 1: File exists on disk — serve directly ────────
                    File file = new File(r.getPdfPath());
                    if (file.exists()) {
                        log.info("📁  Serving PDF from disk: {}", r.getPdfPath());
                        Resource resource = new FileSystemResource(file);
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"QA_Report_" + jobId + ".pdf\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .<Resource>body(resource);
                    }

                    // ── Option 2: File gone — fetch from Cloudinary and stream back ──
                    if (r.getPublicUrl() != null && !r.getPublicUrl().isBlank()) {
                        log.info("☁️  PDF not on disk — fetching from Cloudinary: {}",
                                r.getPublicUrl());
                        try {
                            // Fetch PDF bytes from Cloudinary on the server side
                            RestClient restClient = RestClient.create();
                            byte[] pdfBytes = restClient.get()
                                    .uri(r.getPublicUrl())
                                    .retrieve()
                                    .body(byte[].class);

                            if (pdfBytes == null || pdfBytes.length == 0) {
                                log.warn("⚠️  Cloudinary returned empty response for job: {}", jobId);
                                return ResponseEntity.<Resource>notFound().build();
                            }

                            // Wrap bytes as a Resource and stream to client
                            Resource resource = new ByteArrayResource(pdfBytes);
                            log.info("✅  Streaming {} bytes from Cloudinary for job: {}",
                                    pdfBytes.length, jobId);

                            return ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION,
                                            "attachment; filename=\"QA_Report_" + jobId + ".pdf\"")
                                    .contentType(MediaType.APPLICATION_PDF)
                                    .<Resource>body(resource);

                        } catch (Exception e) {
                            log.error("❌  Failed to fetch PDF from Cloudinary for job {}: {}",
                                    jobId, e.getMessage());
                            return ResponseEntity.<Resource>internalServerError().build();
                        }
                    }

                    // ── Option 3: Neither available ────────────────────────────
                    log.warn("⚠️  PDF unavailable — no disk file and no Cloudinary URL: {}",
                            jobId);
                    return ResponseEntity.<Resource>notFound().build();
                })
                .orElseGet(() -> {
                    log.warn("⚠️  Job {} not found or not owned by {}", jobId, userEmail);
                    return ResponseEntity.<Resource>notFound().build();
                });
    }
}