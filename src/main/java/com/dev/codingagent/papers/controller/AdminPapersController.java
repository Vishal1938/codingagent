package com.dev.codingagent.papers.controller;

import com.dev.codingagent.papers.dto.AdminUploadPaperRequest;
import com.dev.codingagent.papers.dto.PaperSummaryDto;
import com.dev.codingagent.papers.entity.Paper;
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

import java.util.Map;

/**
 * Admin-only endpoints for managing the Past Papers archive.
 * All endpoints require role=ADMIN.
 *
 * PR-1 ships only the upload endpoint. Pending review + approve/reject
 * will land in PR-3 alongside the community sharing flow.
 */
@RestController
@RequestMapping("/api/admin/papers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPapersController {

    private static final Logger log = LoggerFactory.getLogger(AdminPapersController.class);

    private final PaperUploadService uploadService;

    public AdminPapersController(PaperUploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * Upload a new paper to the archive.
     *
     * Multipart fields:
     *  - file:           the original paper PDF
     *  - title:          "CBSE Class 10 Mathematics 2024"
     *  - board:          CBSE | ICSE | etc.
     *  - classLevel:     "10"
     *  - subject:        "Mathematics"
     *  - year:           2024
     *  - totalMarks:     80 (optional)
     *  - durationMinutes: 180 (optional)
     */
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
}