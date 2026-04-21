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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PageQuestionExtractorService {

    private static final Logger log = LoggerFactory.getLogger(PageQuestionExtractorService.class);

    private final ChatClient detectorClient;
    private final ChatClient extractorClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PageQuestionExtractorService(ChatClient.Builder builder) {

        // ── Client 1: Detects if a page has questions ──────────────
        this.detectorClient = builder
                .defaultSystem("""
                You are a PAGE CLASSIFIER for academic question papers.
                You will receive raw text from ONE page of a document.
                
                YOUR ONLY OUTPUT must be this exact JSON object — nothing else:
                {
                  "hasQuestions": true or false,
                  "reason": "<one short sentence>",
                  "questionCount": <number>
                }
                
                DO NOT extract questions.
                DO NOT return an array.
                DO NOT add any explanation or markdown.
                ONLY return the JSON object above.
                
                Classify as true if the page has:
                - Numbered questions (1. 2. Q1. Q2.)
                - Questions ending with ?
                - Fill in the blank lines
                - MCQ options (a) b) c) d))
                - Tasks: Explain/Describe/Calculate/Find/Solve
                
                Classify as false if the page only has:
                - Instructions or rules
                - Time limits, marks breakdown
                - College name, exam title, date
                - Section headers with no questions
                - Blank content
                """)
                .build();

        // ── Client 2: Extracts questions from a confirmed page ─────
        this.extractorClient = builder
                .defaultSystem("""
                        You are a question extractor for academic question papers.
                        You will receive raw text from a single page that is confirmed
                        to contain exam questions.
                        
                        Your job:
                        1. IGNORE all instructions, headers, time limits, marks summaries
                        2. EXTRACT only the actual questions students need to answer
                        3. PRESERVE the exact wording of each question
                        4. DETECT the question type
                        5. EXTRACT marks if written next to the question
                        6. DETECT the section name if mentioned on this page
                        
                        Respond ONLY with a JSON array — no explanation, no markdown:
                        [
                          {
                            "question": "<exact question text>",
                            "type": "<MCQ|short_answer|descriptive|numerical|true_false|fill_blank>",
                            "marks": <number or null>,
                            "section": "<section name or null>"
                          }
                        ]
                        If somehow no questions found after all, return: []
                        """)
                .build();
    }

    public DocumentExtractionResponse extractFromPdf(MultipartFile file) throws IOException {
        long start = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();

        log.info("═══════════════════════════════════════════════");
        log.info("📄  Starting page-by-page extraction: {}", fileName);

        List<String> pages = extractPagesText(file);
        int totalPages = pages.size();
        log.info("📖  Total pages detected: {}", totalPages);

        List<PageExtractionResult> pageResults = new ArrayList<>();
        List<ExtractedQuestion> allQuestions = new ArrayList<>();
        AtomicInteger globalId = new AtomicInteger(1);
        int pagesWithQuestions = 0;

        for (int i = 0; i < pages.size(); i++) {
            int pageNum = i + 1;
            String pageText = pages.get(i);

            log.info("──────────────────────────────────────────────");
            log.info("📃  Processing page {}/{}", pageNum, totalPages);

            if (pageText == null || pageText.isBlank()) {
                log.info("⏭️  Page {} — blank, skipping", pageNum);
                pageResults.add(new PageExtractionResult(
                        pageNum, false, "Blank page", List.of()
                ));
                continue;
            }

            // ── Step 1: Detect if page has questions ──────────────
            PageDetectionResult detection = detectQuestions(pageText, pageNum);
            log.info("🔍  Page {} — hasQuestions: {} | reason: {} | estimated: {}",
                    pageNum, detection.hasQuestions(),
                    detection.reason(), detection.questionCount());

            if (!detection.hasQuestions()) {
                pageResults.add(new PageExtractionResult(
                        pageNum, false, detection.reason(), List.of()
                ));
                continue;
            }

            // ── Step 2: Extract questions from this page ──────────
            pagesWithQuestions++;
            log.info("✅  Page {} has questions — extracting...", pageNum);
            List<ExtractedQuestion> pageQuestions =
                    extractQuestionsFromPage(pageText, pageNum, globalId);

            log.info("📝  Page {} — {} questions extracted", pageNum, pageQuestions.size());
            allQuestions.addAll(pageQuestions);
            pageResults.add(new PageExtractionResult(
                    pageNum, true, detection.reason(), pageQuestions
            ));
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("═══════════════════════════════════════════════");
        log.info("✅  Extraction complete in {}ms", elapsed);
        log.info("📊  Pages with questions: {}/{}", pagesWithQuestions, totalPages);
        log.info("📊  Total questions found: {}", allQuestions.size());

        return new DocumentExtractionResponse(
                fileName,
                totalPages,
                pagesWithQuestions,
                totalPages - pagesWithQuestions,
                allQuestions.size(),
                elapsed,
                pageResults,
                allQuestions
        );
    }

    // ── Extract text page by page using PDFBox ────────────────────
    private List<String> extractPagesText(MultipartFile file) throws IOException {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            int numPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= numPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                pages.add(text != null ? text.trim() : "");
            }
        }
        return pages;
    }

    // ── Ask LLM: does this page have questions? ───────────────────
    private PageDetectionResult detectQuestions(String pageText, int pageNum) {
        try {
            String prompt = """
                Classify this page. Does it contain exam questions?
                
                Page text:
                ---
                %s
                ---
                
                Reply ONLY with this JSON object:
                {"hasQuestions": true or false, "reason": "<one sentence>", "questionCount": <number>}
                """.formatted(pageText);

            String raw = detectorClient.prompt()
                    .user(prompt)
                    .call().content();

            log.info("🔍  Raw detection response page {}: {}", pageNum, raw);
            String cleaned = cleanJson(raw);
            log.info("🔍  Cleaned detection response page {}: {}", pageNum, cleaned);

            // ── If LLM returned an array instead of object ─────────
            if (cleaned.trim().startsWith("[")) {
                log.warn("⚠️  Page {} — LLM returned array instead of detection object, inferring from array size", pageNum);
                List<?> list = objectMapper.readValue(cleaned, List.class);
                boolean hasQ = !list.isEmpty();
                return new PageDetectionResult(hasQ,
                        hasQ ? "LLM returned questions directly — page has questions" : "Empty array",
                        list.size());
            }

            return objectMapper.readValue(cleaned, PageDetectionResult.class);

        } catch (Exception e) {
            log.error("❌  Detection failed for page {}: {}", pageNum, e.getMessage());
            return new PageDetectionResult(false, "Detection failed: " + e.getMessage(), 0);
        }
    }

    // ── Ask LLM: extract questions from this page ─────────────────
    private List<ExtractedQuestion> extractQuestionsFromPage(
            String pageText, int pageNum, AtomicInteger globalId) {
        try {
            String prompt = """
                    Extract all exam questions from this page text.
                    
                    Page text:
                    ---
                    %s
                    ---
                    """.formatted(pageText);

            String raw = extractorClient.prompt()
                    .user(prompt)
                    .call().content();

            String cleaned = cleanJson(raw);

            // Parse into intermediate list then map with globalId + pageNum
            List<RawExtractedQuestion> rawList = objectMapper.readValue(
                    cleaned, new TypeReference<List<RawExtractedQuestion>>() {}
            );

            return rawList.stream()
                    .map(q -> new ExtractedQuestion(
                            globalId.getAndIncrement(),
                            q.question(),
                            q.type(),
                            q.marks(),
                            q.section(),
                            pageNum
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("❌  Extraction failed for page {}: {}", pageNum, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Strip markdown fences from LLM response ───────────────────
    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.replaceAll("```json", "").replaceAll("```", "").trim();

        // For object responses
        int objStart = cleaned.indexOf('{');
        int objEnd = cleaned.lastIndexOf('}');

        // For array responses
        int arrStart = cleaned.indexOf('[');
        int arrEnd = cleaned.lastIndexOf(']');

        // Return whichever valid structure appears first
        if (arrStart != -1 && arrEnd != -1 &&
                (objStart == -1 || arrStart < objStart)) {
            return cleaned.substring(arrStart, arrEnd + 1);
        }
        if (objStart != -1 && objEnd != -1) {
            return cleaned.substring(objStart, objEnd + 1);
        }
        return cleaned;
    }

    // ── Internal records for JSON parsing ─────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageDetectionResult(
            boolean hasQuestions,
            String reason,
            int questionCount
    ) {}

    private record RawExtractedQuestion(
            String question,
            String type,
            Integer marks,
            String section
    ) {}
}