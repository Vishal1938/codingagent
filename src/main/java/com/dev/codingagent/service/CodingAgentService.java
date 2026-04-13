package com.dev.codingagent.service;

import com.dev.codingagent.dto.AgentResponse;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CodingAgentService {

    private static final Logger log = LoggerFactory.getLogger(CodingAgentService.class);

    private final ChatClient chatClient;   // ✅ built only ONCE
    private final String workingDir = System.getProperty("user.dir");
    private final AtomicInteger promptCounter = new AtomicInteger(0);

    @Value("${agent.system.prompt}")
    private String defaultSystemPrompt;

    public CodingAgentService(ChatClient.Builder builder) {
        log.info("═══════════════════════════════════════════════");
        log.info("🤖  Coding Agent Initializing...");
        log.info("📂  Working Directory : {}", workingDir);
        log.info("☕  Java Version      : {}", System.getProperty("java.version"));
        log.info("🖥️  OS                : {}", System.getProperty("os.name"));
        log.info("═══════════════════════════════════════════════");

        // ✅ Build once with tools — tools are registered only one time
        this.chatClient = builder
                .defaultTools(
                        FileSystemTools.builder().build(),
                        GrepTool.builder().build(),
                        GlobTool.builder().build(),
                        ShellTools.builder().build()
                )
                .build();

        log.info("✅  ChatClient built successfully");
        log.info("🔧  Tools registered: FileSystemTools, GrepTool, GlobTool, ShellTools");
    }

    // ── Resolve which system prompt to use ─────────────────────────
    private String resolveSystemPrompt(String payloadSystemPrompt) {
        if (payloadSystemPrompt != null && !payloadSystemPrompt.isBlank()) {
            log.info("🔄  Using system prompt from payload");
            return payloadSystemPrompt + "\n\nCurrent directory: " + workingDir;
        }
        log.info("🔄  Using default system prompt from application.properties");
        return defaultSystemPrompt + "\n\nCurrent directory: " + workingDir;
    }

    public AgentResponse chat(String prompt, String filePath, String systemPrompt) {
        int questionNumber = promptCounter.incrementAndGet();

        log.info("──────────────────────────────────────────────");
        log.info("📨  [Q-{}] Prompt received", questionNumber);
        log.info("💬  Input: {}", prompt);

        String finalPrompt;
        if (filePath != null && !filePath.isBlank()) {
            log.info("📄  File path provided: {}", filePath);
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                log.warn("⚠️  File not found at path: {}", filePath);
                return new AgentResponse("File not found at path: " + filePath, workingDir, 0, questionNumber, null);
            }
            finalPrompt = prompt + "\n\nFile to work with: " + filePath;
        } else {
            log.info("📂  No file path provided, using working directory: {}", workingDir);
            finalPrompt = prompt;
        }

        logIntentHint(prompt);
        long start = System.currentTimeMillis();
        log.info("⏳  Sending prompt to LLM...");

        // ✅ system prompt injected per request via .system(), not via builder
        String response = chatClient.prompt()
                .system(resolveSystemPrompt(systemPrompt))
                .user(finalPrompt)
                .toolContext(Map.of("workingDir", workingDir, "filePath", filePath != null ? filePath : ""))
                .call().content();

        long elapsed = System.currentTimeMillis() - start;
        log.info("✅  Response received in {}ms", elapsed);
        log.info("📝  Response length: {} characters", response != null ? response.length() : 0);

        return new AgentResponse(response, workingDir, elapsed, questionNumber, null);
    }

    public AgentResponse analyzeFile(String prompt, MultipartFile file, String systemPrompt) {
        int questionNumber = promptCounter.incrementAndGet();

        log.info("──────────────────────────────────────────────");
        log.info("📨  [Q-{}] File upload request received", questionNumber);
        log.info("💬  Prompt: {}", prompt);
        log.info("📄  File name: {}", file.getOriginalFilename());
        log.info("📦  File size: {} bytes", file.getSize());

        String fileContent;
        try {
            fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            log.info("✅  File read successfully — {} characters", fileContent.length());
        } catch (IOException e) {
            log.error("❌  Failed to read uploaded file: {}", e.getMessage());
            return new AgentResponse("Failed to read uploaded file: " + e.getMessage(), workingDir, 0, questionNumber, file.getOriginalFilename());
        }

        String finalPrompt = """
                %s

                File name: %s
                File content:%s""".formatted(prompt, file.getOriginalFilename(), fileContent);

        log.info("⏳  Sending prompt + file content to LLM...");
        long start = System.currentTimeMillis();

        // ✅ system prompt injected per request via .system(), not via builder
        String response = chatClient.prompt()
                .system(resolveSystemPrompt(systemPrompt))
                .user(finalPrompt)
                .toolContext(Map.of("workingDir", workingDir))
                .call().content();

        long elapsed = System.currentTimeMillis() - start;
        log.info("✅  Response received in {}ms", elapsed);

        return new AgentResponse(response, workingDir, elapsed, questionNumber, file.getOriginalFilename());
    }

    private void logIntentHint(String input) {
        String lower = input.toLowerCase();
        if (lower.contains("read") || lower.contains("open") || lower.contains("show file")) {
            log.info("🔍  Intent: FILE READ — looking inside {}", workingDir);
        } else if (lower.contains("search") || lower.contains("grep") || lower.contains("find")) {
            log.info("🔎  Intent: SEARCH — scanning files under {}", workingDir);
        } else if (lower.contains("list") || lower.contains("files") || lower.contains("directory")) {
            log.info("📁  Intent: DIRECTORY LISTING — browsing {}", workingDir);
        } else if (lower.contains("run") || lower.contains("execute") || lower.contains("shell")) {
            log.info("⚙️  Intent: SHELL COMMAND — executing in {}", workingDir);
        } else if (lower.contains("edit") || lower.contains("write") || lower.contains("create")) {
            log.info("✏️  Intent: FILE WRITE/EDIT — target {}", workingDir);
        } else {
            log.info("💭  Intent: GENERAL QUERY");
        }
    }
}