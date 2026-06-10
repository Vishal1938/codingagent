package com.dev.codingagent.papers.controller;


import com.dev.codingagent.papers.dto.PaperSummaryDto;
import com.dev.codingagent.papers.dto.ShareRequest;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.service.CommunityShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User-facing community sharing endpoints.
 * Lets a user submit their own report to the public archive (pending review),
 * or withdraw a pending submission.
 */
@RestController
@RequestMapping("/api/papers/share")
public class CommunityController {

    private static final Logger log = LoggerFactory.getLogger(CommunityController.class);

    private final CommunityShareService shareService;

    public CommunityController(CommunityShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * Share one of the user's own reports to the community archive.
     * The paper enters the pending queue and appears publicly only after admin approval.
     */
    @PutMapping("/{jobId}")
    public ResponseEntity<?> share(
            @PathVariable String jobId,
            @RequestBody ShareRequest request,
            @AuthenticationPrincipal UserDetails user) {

        log.info("📡  PUT /api/papers/share/{} | by {}", jobId, user.getUsername());

        // Basic validation
        if (request.board() == null || request.classLevel() == null
                || request.subject() == null || request.year() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "board, classLevel, subject, and year are required"));
        }

        try {
            Paper paper = shareService.shareReport(jobId, user.getUsername(), request);
            return ResponseEntity.ok(Map.of(
                    "message", "Submitted for review. It'll appear in the archive once approved.",
                    "paper", PaperSummaryDto.from(paper)
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌  Share failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Share failed: " + e.getMessage()));
        }
    }

    /**
     * Withdraw a pending submission (only works while still pending).
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<?> unshare(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserDetails user) {

        log.info("📡  DELETE /api/papers/share/{} | by {}", jobId, user.getUsername());

        try {
            boolean withdrawn = shareService.unshareReport(jobId, user.getUsername());
            if (withdrawn) {
                return ResponseEntity.ok(Map.of("message", "Submission withdrawn"));
            } else {
                return ResponseEntity.ok(Map.of(
                        "message", "Nothing pending to withdraw — the submission link was cleared."));
            }
        } catch (IllegalStateException e) {
            // e.g. "already approved and published"
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}