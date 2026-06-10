package com.dev.codingagent.papers.controller;

import com.dev.codingagent.papers.dto.AdminUploadPaperRequest;
import com.dev.codingagent.papers.dto.PaperSummaryDto;
import com.dev.codingagent.papers.dto.PendingPaperDto;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.service.PaperModerationService;
import com.dev.codingagent.papers.service.PaperUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for managing the Past Papers archive.
 * PR-1: upload. PR-3: pending review queue + approve/reject.
 * All endpoints require role=ADMIN.
 */
@RestController
@RequestMapping("/api/admin/papers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPapersController {

    private static final Logger log = LoggerFactory.getLogger(AdminPapersController.class);

    private final PaperUploadService     uploadService;
    private final PaperModerationService moderationService;

    public AdminPapersController(PaperUploadService uploadService,
                                 PaperModerationService moderationService) {
        this.uploadService     = uploadService;
        this.moderationService = moderationService;
    }

    // ── PR-1: Upload a new paper ─────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("board") String board,
            @RequestParam("classLevel") String classLevel,
            @RequestParam("subject") String subject,
            @RequestParam("year") Integer year,
            @RequestParam(value = "totalMarks", required = false) Integer totalMarks,
            @RequestParam(value = "durationMinutes", required = false) Integer durationMinutes,
            @AuthenticationPrincipal UserDetails admin) {

        log.info("📡  POST /api/admin/papers | {} | admin: {}", title, admin.getUsername());
        try {
            AdminUploadPaperRequest meta = new AdminUploadPaperRequest(
                    title, board, classLevel, subject, year, totalMarks, durationMinutes);
            Paper saved = uploadService.upload(file, meta, admin.getUsername());
            return ResponseEntity.ok(PaperSummaryDto.from(saved));
        } catch (Exception e) {
            log.error("❌  Paper upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // ── PR-3: Pending review queue ───────────────────────────────────
    @GetMapping("/pending")
    public ResponseEntity<List<PendingPaperDto>> pending() {
        log.info("📡  GET /api/admin/papers/pending");
        return ResponseEntity.ok(moderationService.getPendingPapers());
    }

    // ── PR-3: Approve a pending paper ────────────────────────────────
    @PutMapping("/{paperId}/approve")
    public ResponseEntity<?> approve(@PathVariable String paperId,
                                     @AuthenticationPrincipal UserDetails admin) {
        log.info("📡  PUT /api/admin/papers/{}/approve | by {}", paperId, admin.getUsername());
        return moderationService.approve(paperId, admin.getUsername())
                .map(p -> ResponseEntity.ok(Map.of(
                        "message", "Paper approved and published",
                        "paper", PaperSummaryDto.from(p))))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── PR-3: Reject a pending paper ─────────────────────────────────
    @PutMapping("/{paperId}/reject")
    public ResponseEntity<?> reject(@PathVariable String paperId,
                                    @AuthenticationPrincipal UserDetails admin) {
        log.info("📡  PUT /api/admin/papers/{}/reject | by {}", paperId, admin.getUsername());
        boolean rejected = moderationService.reject(paperId, admin.getUsername());
        return rejected
                ? ResponseEntity.ok(Map.of("message", "Paper rejected and removed"))
                : ResponseEntity.badRequest().body(Map.of("error",
                "Could not reject — paper not found or already approved"));
    }
}