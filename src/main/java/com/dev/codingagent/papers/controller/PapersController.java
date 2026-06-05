package com.dev.codingagent.papers.controller;

import com.dev.codingagent.papers.dto.FilterOptionsDto;
import com.dev.codingagent.papers.dto.PaperDetailDto;
import com.dev.codingagent.papers.dto.PaperSearchResponse;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.service.PaperBrowseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Public (authenticated) endpoints for browsing the Past Papers archive.
 * Any logged-in user can access these.
 */
@RestController
@RequestMapping("/api/papers")
public class PapersController {

    private static final Logger log = LoggerFactory.getLogger(PapersController.class);

    private final PaperBrowseService browseService;

    public PapersController(PaperBrowseService browseService) {
        this.browseService = browseService;
    }

    // ── Search / browse with filters ─────────────────────────────────
    @GetMapping("/search")
    public ResponseEntity<PaperSearchResponse> search(
            @RequestParam(required = false) String board,
            @RequestParam(required = false) String classLevel,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String q,          // title search
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {

        log.info("📡  GET /api/papers/search | board={} class={} subject={} year={} q={} page={}",
                board, classLevel, subject, year, q, page);

        PaperSearchResponse response =
                browseService.search(board, classLevel, subject, year, q, page, size);
        return ResponseEntity.ok(response);
    }

    // ── Filter dropdown options ──────────────────────────────────────
    @GetMapping("/filters")
    public ResponseEntity<FilterOptionsDto> getFilters() {
        return ResponseEntity.ok(browseService.getFilterOptions());
    }

    // ── Paper detail (metadata + questions) ──────────────────────────
    @GetMapping("/{paperId}")
    public ResponseEntity<PaperDetailDto> getPaper(@PathVariable String paperId) {
        log.info("📡  GET /api/papers/{}", paperId);
        return browseService.getPaperDetail(paperId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Download the pre-generated answer PDF ────────────────────────
    @GetMapping("/{paperId}/download")
    public ResponseEntity<Resource> download(@PathVariable String paperId) {
        log.info("📡  GET /api/papers/{}/download", paperId);

        return browseService.recordDownloadAndGet(paperId)
                .filter(p -> p.getAnswerPdfUrl() != null && !p.getAnswerPdfUrl().isBlank())
                .map(this::streamFromCloudinary)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helper: fetch PDF from Cloudinary, stream to client ──────────
    private ResponseEntity<Resource> streamFromCloudinary(Paper paper) {
        try {
            RestClient restClient = RestClient.create();
            byte[] pdfBytes = restClient.get()
                    .uri(paper.getAnswerPdfUrl())
                    .retrieve()
                    .body(byte[].class);

            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }

            String safeTitle = paper.getTitle().replaceAll("[^a-zA-Z0-9]", "_");
            Resource resource = new ByteArrayResource(pdfBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + safeTitle + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            log.error("❌  Failed to stream PDF for paper {}: {}",
                    paper.getPaperId(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}