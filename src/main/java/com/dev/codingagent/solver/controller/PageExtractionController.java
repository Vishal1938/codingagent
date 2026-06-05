package com.dev.codingagent.solver.controller;


import com.dev.codingagent.solver.dto.DocumentExtractionResponse;
import com.dev.codingagent.solver.service.PageQuestionExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/extractor")
public class PageExtractionController {

    private static final Logger log = LoggerFactory.getLogger(PageExtractionController.class);
    private final PageQuestionExtractorService extractorService;

    public PageExtractionController(PageQuestionExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentExtractionResponse> extract(
            @RequestParam("file") MultipartFile file) {

        log.info("📡  POST /api/extractor/extract");
        log.info("📄  File: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            log.warn("⚠️  Empty file received");
            return ResponseEntity.badRequest().build();
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            return ResponseEntity.badRequest().build();
        }

        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".txt")) {
            log.warn("⚠️  Unsupported file type: {}", fileName);
            return ResponseEntity.badRequest().build();
        }

        try {
            DocumentExtractionResponse response = extractorService.extractFromPdf(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌  Extraction error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}