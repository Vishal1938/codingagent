package com.dev.codingagent.doubt.controller;

import com.dev.codingagent.doubt.dto.DocJob;
import com.dev.codingagent.doubt.dto.DocJobStore;
import com.dev.codingagent.doubt.dto.DocumentSummaryDto;
import com.dev.codingagent.doubt.repository.DocumentChunkRepository;
import com.dev.codingagent.doubt.repository.DocumentRepository;
import com.dev.codingagent.doubt.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document endpoints for Doubt Chat — PR-1: upload (async embed), status,
 * library, delete. Chat endpoints land in PR-2.
 */
@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentIngestionService ingestionService;
    private final DocJobStore              jobStore;
    private final DocumentRepository       documentRepository;
    private final DocumentChunkRepository  chunkRepository;

    public DocumentController(DocumentIngestionService ingestionService,
                              DocJobStore jobStore,
                              DocumentRepository documentRepository,
                              DocumentChunkRepository chunkRepository) {
        this.ingestionService   = ingestionService;
        this.jobStore           = jobStore;
        this.documentRepository = documentRepository;
        this.chunkRepository    = chunkRepository;
    }

    /** Upload a document → async chunk + embed. Returns jobId to poll. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @AuthenticationPrincipal UserDetails user) {

        log.info("📡  POST /api/docs/upload | {} | user: {}",
                file.getOriginalFilename(), user.getUsername());

        try {
            byte[] bytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            String jobId = UUID.randomUUID().toString();
            DocJob job = new DocJob(jobId, fileName);
            jobStore.put(job);

            ingestionService.ingestAsync(jobId, bytes, fileName, title, user.getUsername());

            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId, "status", "PENDING",
                    "message", "Processing document. Poll /status/" + jobId));
        } catch (Exception e) {
            log.error("❌  Doc upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start: " + e.getMessage()));
        }
    }

    /** Poll ingestion status. */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> status(@PathVariable String jobId) {
        DocJob job = jobStore.get(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "jobId", job.getJobId(),
                "status", job.getStatus(),
                "documentId", job.getDocumentId() == null ? "" : job.getDocumentId(),
                "chunkCount", job.getChunkCount() == null ? 0 : job.getChunkCount(),
                "error", job.getError() == null ? "" : job.getError()));
    }

    /** User's document library. */
    @GetMapping("/my")
    public ResponseEntity<List<DocumentSummaryDto>> myDocs(
            @AuthenticationPrincipal UserDetails user) {
        List<DocumentSummaryDto> docs = documentRepository
                .findByUserIdOrderByCreatedAtDesc(user.getUsername())
                .stream().map(DocumentSummaryDto::from).toList();
        return ResponseEntity.ok(docs);
    }

    /** Delete a document + its chunks. */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> delete(@PathVariable String documentId,
                                    @AuthenticationPrincipal UserDetails user) {
        return documentRepository.findByDocumentIdAndUserId(documentId, user.getUsername())
                .map(doc -> {
                    chunkRepository.deleteByDocumentId(documentId);
                    documentRepository.deleteByDocumentId(documentId);
                    log.info("🗑️  Deleted document {}", documentId);
                    return ResponseEntity.ok(Map.of("message", "Document deleted"));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Document not found")));
    }
}