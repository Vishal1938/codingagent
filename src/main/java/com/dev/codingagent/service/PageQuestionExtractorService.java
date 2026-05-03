package com.dev.codingagent.service;

import com.dev.codingagent.dto.DocumentExtractionResponse;
import com.dev.codingagent.dto.ExtractedQuestion;
import com.dev.codingagent.dto.PageExtractionResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PageQuestionExtractorService {

    private static final Logger log = LoggerFactory.getLogger(PageQuestionExtractorService.class);

    private static final int MAX_PAGE_CHARS   = 12000;
    private static final int PARALLEL_THREADS = 4;
    private static final int MAX_RETRIES      = 2;

    /*
     * FIX: Use a SINGLE shared ChatClient with NO defaultSystem set on the builder.
     * Instead, we pass the system prompt on every individual .prompt() call via
     * .system(...). This completely avoids the shared-builder mutation problem —
     * there is no state to accidentally overwrite.
     */
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_THREADS);

    // System prompts stored as constants — clean, readable, and easy to tune.
    private static final String DETECTOR_SYSTEM = """
            You are a PAGE CLASSIFIER for academic question papers.
            Analyze the raw text from ONE page of a document.

            YOU MUST reply with ONLY this JSON object — no markdown, no extra text, nothing else:
            {"hasQuestions": true, "reason": "one short sentence", "questionCount": 3}

            Set "hasQuestions" to true if the page contains ANY of:
            - Numbered questions  (1. 2. Q1. Q2. (i) (ii))
            - Questions ending with ?
            - Fill-in-the-blank lines  (______)
            - MCQ options  (a) b) c) d)  or  (A) (B) (C) (D))
            - Task verbs applied to a topic  (Explain / Describe / Calculate / Find / Solve / Derive / Prove)

            Set "hasQuestions" to false if the page contains ONLY:
            - Exam header  (college name, subject, date, time, max marks)
            - Pure instructions or rules  (attempt all questions, use blue pen)
            - Section heading with zero question text  (SECTION A — no body)
            - Completely blank content

            STRICT RULES:
            - "hasQuestions" must be a boolean  (true or false), NOT a string.
            - "questionCount" must be an integer  (0 if hasQuestions is false).
            - Do NOT wrap the response in ```json fences.
            - Do NOT add any explanation before or after the JSON.
            """;

    private static final String EXTRACTOR_SYSTEM = """
            You are a question extractor for academic exam papers.
            You will receive raw text from a single page that contains exam questions.

            Rules:
            1. Extract ONLY the actual questions students must answer.
            2. Preserve the exact wording of every question — do not paraphrase.
            3. Ignore headers, instructions, time limits, and marks summaries.
            4. Detect the question type from: MCQ, short_answer, descriptive, numerical, true_false, fill_blank.
            5. Extract the marks value if written next to the question (e.g. "[5 marks]" → 5); null if absent.
            6. Detect the section name if mentioned on this page (e.g. "SECTION B"); null if absent.

            YOU MUST reply with ONLY a JSON array — no markdown, no preamble, no trailing text:
            [
              {
                "question": "<exact question text>",
                "type": "<MCQ|short_answer|descriptive|numerical|true_false|fill_blank>",
                "marks": <integer or null>,
                "section": "<section name or null>"
              }
            ]
            If no questions are found, return an empty array: []
            Do NOT wrap the output in ```json fences.
            """;

    public PageQuestionExtractorService(ChatClient.Builder builder) {
        // Build once, no defaultSystem — system prompt goes in each call instead.
        this.chatClient = builder.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    public DocumentExtractionResponse extractFromPdf(MultipartFile file) throws IOException {
        long   start    = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();

        log.info("═══════════════════════════════════════════════");
        log.info("📄  Starting extraction: {}", fileName);

        List<String> pages      = extractPagesText(file);
        int          totalPages = pages.size();
        log.info("📖  Total pages: {}", totalPages);

        AtomicInteger globalId = new AtomicInteger(1);
        List<CompletableFuture<PageExtractionResult>> futures = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            final int    pageNum  = i + 1;
            final String pageText = pages.get(i);
            futures.add(CompletableFuture.supplyAsync(
                    () -> processPage(pageNum, totalPages, pageText, globalId),
                    executor
            ));
        }

        List<PageExtractionResult> pageResults = futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(PageExtractionResult::pageNumber))
                .toList();

        List<ExtractedQuestion> allQuestions = pageResults.stream()
                .flatMap(r -> r.questions().stream())
                .toList();

        long pagesWithQuestions = pageResults.stream()
                .filter(PageExtractionResult::hasQuestions)
                .count();

        long elapsed = System.currentTimeMillis() - start;
        log.info("═══════════════════════════════════════════════");
        log.info("✅  Done in {}ms | pages-with-questions: {}/{} | questions: {}",
                elapsed, pagesWithQuestions, totalPages, allQuestions.size());

        return new DocumentExtractionResponse(
                fileName,
                totalPages,
                (int) pagesWithQuestions,
                (int) (totalPages - pagesWithQuestions),
                allQuestions.size(),
                elapsed,
                pageResults,
                allQuestions
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-page pipeline
    // ─────────────────────────────────────────────────────────────────────────

    private PageExtractionResult processPage(int pageNum, int totalPages,
                                             String pageText, AtomicInteger globalId) {
        log.info("📃  Page {}/{}", pageNum, totalPages);

        if (pageText == null || pageText.isBlank()) {
            log.info("⏭️  Page {} — blank, skipping", pageNum);
            return new PageExtractionResult(pageNum, false, "Blank page", List.of());
        }

        String truncated = truncate(pageText, MAX_PAGE_CHARS);

        // Step 1 — detect
        PageDetectionResult detection = detectWithRetry(truncated, pageNum);
        log.info("🔍  Page {} — hasQuestions={} | reason='{}' | count={}",
                pageNum, detection.hasQuestions(), detection.reason(), detection.questionCount());

        if (!Boolean.TRUE.equals(detection.hasQuestions())) {
            return new PageExtractionResult(pageNum, false, detection.reason(), List.of());
        }

        // Step 2 — extract
        log.info("✅  Page {} — extracting...", pageNum);
        List<ExtractedQuestion> questions = extractWithRetry(truncated, pageNum, globalId);
        log.info("📝  Page {} — {} questions extracted", pageNum, questions.size());

        return new PageExtractionResult(pageNum, true, detection.reason(), questions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry wrappers
    // ─────────────────────────────────────────────────────────────────────────

    private PageDetectionResult detectWithRetry(String pageText, int pageNum) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            PageDetectionResult result = detectQuestions(pageText, pageNum);
            if (result != null) return result;
            log.warn("🔄  Detection retry {}/{} for page {}", attempt, MAX_RETRIES, pageNum);
        }
        log.error("❌  Detection failed for page {} after all retries", pageNum);
        return new PageDetectionResult(false, "Detection failed after retries", 0);
    }

    private List<ExtractedQuestion> extractWithRetry(String pageText, int pageNum,
                                                     AtomicInteger globalId) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            List<ExtractedQuestion> result = extractQuestionsFromPage(pageText, pageNum, globalId);
            if (result != null) return result;
            log.warn("🔄  Extraction retry {}/{} for page {}", attempt, MAX_RETRIES, pageNum);
        }
        log.error("❌  Extraction failed for page {} after all retries", pageNum);
        return List.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File text extraction
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> extractPagesText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.toLowerCase().endsWith(".txt")) {
            return extractTextFilePages(file);
        }
        return extractPdfPages(file);
    }

    private List<String> extractPdfPages(MultipartFile file) throws IOException {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int numPages = document.getNumberOfPages();
            for (int i = 1; i <= numPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                pages.add(text != null ? text.trim() : "");
            }
        }
        return pages;
    }

    private List<String> extractTextFilePages(MultipartFile file) throws IOException {
        String       fullText = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<String> pages    = new ArrayList<>();
        int chunkSize = 3000;
        int length    = fullText.length();
        for (int i = 0; i < length; i += chunkSize) {
            String chunk = fullText.substring(i, Math.min(i + chunkSize, length)).trim();
            if (!chunk.isBlank()) pages.add(chunk);
        }
        log.info("📄  TXT file split into {} chunks", pages.size());
        return pages;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM call: detection
    // ─────────────────────────────────────────────────────────────────────────

    private PageDetectionResult detectQuestions(String pageText, int pageNum) {
        try {
            String userPrompt = """
                    Does this exam page contain questions students must answer?

                    === PAGE TEXT ===
                    %s
                    === END PAGE TEXT ===

                    Reply ONLY with this exact JSON (no markdown fences, no extra text):
                    {"hasQuestions": true or false, "reason": "one short sentence", "questionCount": <integer>}
                    """.formatted(pageText);

            // System prompt passed per-call via .system() — fully isolated, no shared state.
            String raw = chatClient.prompt()
                    .system(DETECTOR_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("🔍  Raw detection page {}: {}", pageNum, raw);

            String cleaned = extractJsonObject(raw);
            if (cleaned == null) {
                log.warn("⚠️  Page {} — no JSON object in detection response: {}", pageNum, raw);
                return null;
            }

            // Safety net: if LLM still returned an array, infer from it
            if (cleaned.trim().startsWith("[")) {
                List<?> list = objectMapper.readValue(cleaned, List.class);
                boolean hasQ = !list.isEmpty();
                return new PageDetectionResult(hasQ,
                        hasQ ? "LLM returned questions directly" : "Empty array", list.size());
            }

            PageDetectionResult result = objectMapper.readValue(cleaned, PageDetectionResult.class);
            if (result.hasQuestions() == null) {
                return new PageDetectionResult(false, result.reason(), result.questionCount());
            }
            return result;

        } catch (Exception e) {
            log.error("❌  Detection error page {}: {}", pageNum, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM call: extraction
    // ─────────────────────────────────────────────────────────────────────────

    private List<ExtractedQuestion> extractQuestionsFromPage(String pageText, int pageNum,
                                                             AtomicInteger globalId) {
        try {
            String userPrompt = """
                    Extract all exam questions from the page text below.
                    Follow the instructions in your system prompt exactly.

                    === PAGE TEXT ===
                    %s
                    === END PAGE TEXT ===
                    """.formatted(pageText);

            // System prompt passed per-call via .system() — fully isolated, no shared state.
            String raw = chatClient.prompt()
                    .system(EXTRACTOR_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("📝  Raw extraction page {}: {}", pageNum, raw);

            String cleaned = extractJsonArray(raw);
            if (cleaned == null) {
                log.warn("⚠️  Page {} — no JSON array in extraction response: {}", pageNum, raw);
                return null;
            }

            List<RawExtractedQuestion> rawList = objectMapper.readValue(
                    cleaned, new TypeReference<List<RawExtractedQuestion>>() {}
            );

            return rawList.stream()
                    .filter(q -> q.question() != null && !q.question().isBlank())
                    .map(q -> new ExtractedQuestion(
                            globalId.getAndIncrement(),
                            q.question().trim(),
                            sanitizeType(q.type()),
                            q.marks(),
                            q.section(),
                            pageNum
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("❌  Extraction error page {}: {}", pageNum, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String extractJsonObject(String raw) {
        if (raw == null) return null;
        String s  = raw.replaceAll("(?s)```json|```", "").trim();
        int start = s.indexOf('{');
        int end   = s.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return null;
        return s.substring(start, end + 1);
    }

    private String extractJsonArray(String raw) {
        if (raw == null) return null;
        String s  = raw.replaceAll("(?s)```json|```", "").trim();
        int start = s.indexOf('[');
        int end   = s.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) return null;
        return s.substring(start, end + 1);
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[... truncated ...]";
    }

    private String sanitizeType(String type) {
        if (type == null) return "short_answer";
        return switch (type.toLowerCase().trim()) {
            case "mcq"          -> "MCQ";
            case "short_answer" -> "short_answer";
            case "descriptive"  -> "descriptive";
            case "numerical"    -> "numerical";
            case "true_false"   -> "true_false";
            case "fill_blank"   -> "fill_blank";
            default             -> "short_answer";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal DTOs
    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageDetectionResult(
            Boolean hasQuestions,
            String  reason,
            int     questionCount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawExtractedQuestion(
            String  question,
            String  type,
            Integer marks,
            String  section
    ) {}
}
