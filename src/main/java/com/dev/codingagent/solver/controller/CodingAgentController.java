package com.dev.codingagent.solver.controller;

import com.dev.codingagent.solver.dto.AgentRequest;
import com.dev.codingagent.solver.dto.AgentResponse;
import com.dev.codingagent.solver.service.CodingAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/agent")
public class CodingAgentController {

    private static final Logger log = LoggerFactory.getLogger(CodingAgentController.class);
    private final CodingAgentService agentService;

    public CodingAgentController(CodingAgentService agentService) {
        this.agentService = agentService;
    }

    // ── Main chat endpoint ─────────────────────────────────────────
    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        log.info("  POST /api/agent/chat — prompt: {}", request.prompt());
        log.info("  File path: {}", request.filePath() != null ? request.filePath() : "not provided");

        if (request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        AgentResponse response = agentService.chat(request.prompt(), request.filePath(), request.systemPrompt()); //
        return ResponseEntity.ok(response);
    }


    // ── NEW: File upload endpoint ────
    @PostMapping(value = "/chat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AgentResponse> chatWithFile(
            @RequestParam("prompt") String prompt,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt) {

        log.info("  POST /api/agent/chat/upload");
        log.info("  Prompt: {}", prompt);
        log.info("  File: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        if (prompt == null || prompt.isBlank()) {
            log.warn("  Empty prompt received");
            return ResponseEntity.badRequest().build();
        }

        if (file.isEmpty()) {
            log.warn("  Empty file received");
            return ResponseEntity.badRequest().build();
        }

        AgentResponse response = agentService.analyzeFile(prompt, file,systemPrompt);
        return ResponseEntity.ok(response);
    }

    // ── Health check ───────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("🤖 Coding Agent is running!");
    }

    // ── Working directory info ─────────────────────────────────────
    @GetMapping("/info")
    public ResponseEntity<?> info() {
        return ResponseEntity.ok(java.util.Map.of(
                "workingDir", System.getProperty("user.dir"),
                "javaVersion", System.getProperty("java.version"),
                "os", System.getProperty("os.name"),
                "status", "running"
        ));
    }
}