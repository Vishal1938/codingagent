package com.dev.codingagent.controller;

import com.dev.codingagent.dto.DocumentSummaryResponse;
import com.dev.codingagent.service.PageSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
public class PdfSummaryController {

    private static final Logger log = LoggerFactory.getLogger(PdfSummaryController.class);

    private final PageSummaryService pageSummaryService;

    public PdfSummaryController(PageSummaryService pageSummaryService) {
        this.pageSummaryService = pageSummaryService;
    }

    /**
     * POST /api/pdf/summarize
     *
     * Accepts a PDF file upload, reads it page by page, and returns a plain-English
     * summary of every page along with metadata (title, key topics, page type).
     *
     * Example curl:
     *   curl -X POST http://localhost:8080/api/pdf/summarize \
     *        -F "file=@/path/to/paper.pdf"
     */
    @PostMapping(
            value    = "/summarize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DocumentSummaryResponse> summarize(
            @RequestParam("file") MultipartFile file) {

        // Basic validation before touching the LLM
        if (file.isEmpty()) {
            log.warn("⚠️  Received empty file upload");
            return ResponseEntity.badRequest().build();
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals(MediaType.APPLICATION_PDF_VALUE)) {
            log.warn("⚠️  Unsupported content type: {}", contentType);
            return ResponseEntity.badRequest().build();
        }

        try {
            log.info("📥  Summarize request received for: {}", file.getOriginalFilename());
            DocumentSummaryResponse response = pageSummaryService.summarisePdf(file);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌  Summarization failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}