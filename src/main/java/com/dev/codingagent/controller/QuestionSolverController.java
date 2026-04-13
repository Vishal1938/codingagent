package com.dev.codingagent.controller;

import com.dev.codingagent.dto.QuestionSolverResponse;
import com.dev.codingagent.service.QuestionSolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/solver")
public class QuestionSolverController {

    private static final Logger log = LoggerFactory.getLogger(QuestionSolverController.class);
    private final QuestionSolverService solverService;

    public QuestionSolverController(QuestionSolverService solverService) {
        this.solverService = solverService;
    }

    @PostMapping(value = "/solve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QuestionSolverResponse> solve(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt) {

        log.info("📡  POST /api/solver/solve");
        log.info("📄  File: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            log.warn("⚠️  Empty file received");
            return ResponseEntity.badRequest().build();
        }

        try {
            QuestionSolverResponse response = solverService.solve(file, systemPrompt);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("❌  Invalid input: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌  Solver error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
