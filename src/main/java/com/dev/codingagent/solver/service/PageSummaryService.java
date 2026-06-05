package com.dev.codingagent.solver.service;

import com.dev.codingagent.solver.dto.DocumentSummaryResponse;
import com.dev.codingagent.solver.dto.PageSummary;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

@Service
public class PageSummaryService {

    private static final Logger log = LoggerFactory.getLogger(PageSummaryService.class);

    // Guard against massive pages blowing the LLM context window.
    // ~4000 chars ≈ ~1000 tokens — plenty for summarisation.
    private static final int MAX_PAGE_CHARS = 4000;

    private final ChatClient summaryClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PageSummaryService(ChatClient.Builder builder) {
        this.summaryClient = builder
                .defaultSystem("""
                        You are a document analyst that summarises individual pages of academic PDFs.
                        You will receive the raw extracted text from ONE page.
                        
                        YOUR ONLY OUTPUT must be this exact JSON object — no markdown fences, no extra text:
                        {
                          "title":     "<short title for this page, max 8 words>",
                          "summary":   "<2 to 4 plain-English sentences describing what is on this page>",
                          "keyTopics": "<comma-separated list of key topics or concepts, max 6 items>",
                          "pageType":  "<one of: cover | instructions | questions | answer-key | content | blank>"
                        }
                        
                        pageType rules:
                        - cover       → college / exam title page, no real content
                        - instructions → rules, time limits, marks breakdown, instructions to candidates
                        - questions   → page contains exam questions students must answer
                        - answer-key  → solutions or answer guide
                        - content     → subject matter content (notes, theory, data)
                        - blank       → no meaningful text at all
                        
                        DO NOT wrap the output in ```json fences.
                        DO NOT add any explanation before or after the JSON.
                        """)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    public DocumentSummaryResponse summarisePdf(MultipartFile file) throws IOException {
        long start    = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();

        log.info("═══════════════════════════════════════════════");
        log.info("📄  Starting page-by-page summarisation: {}", fileName);

        List<String> pages     = extractPagesText(file);
        int          totalPages = pages.size();
        log.info("📖  Total pages: {}", totalPages);

        List<PageSummary> summaries      = new ArrayList<>();
        int               processedPages = 0;
        int               skippedPages   = 0;

        for (int i = 0; i < pages.size(); i++) {
            int    pageNum  = i + 1;
            String pageText = pages.get(i);

            log.info("──────────────────────────────────────────────");
            log.info("📃  Summarising page {}/{}", pageNum, totalPages);

            // Skip genuinely blank pages — no point calling the LLM.
            if (pageText == null || pageText.isBlank()) {
                log.info("⏭️  Page {} — blank, skipping", pageNum);
                summaries.add(PageSummary.skipped(pageNum));
                skippedPages++;
                continue;
            }

            // Truncate before sending to avoid context-window overflows.
            String truncated = truncate(pageText, MAX_PAGE_CHARS);

            PageSummary summary = summarisePage(truncated, pageNum);
            summaries.add(summary);
            processedPages++;

            log.info("✅  Page {} — type='{}' | title='{}'",
                    pageNum, summary.pageType(), summary.title());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("═══════════════════════════════════════════════");
        log.info("✅  Summarisation complete in {}ms | processed={} | skipped={}",
                elapsed, processedPages, skippedPages);

        return new DocumentSummaryResponse(
                fileName,
                totalPages,
                processedPages,
                skippedPages,
                elapsed,
                summaries
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM call: summarise one page
    // ─────────────────────────────────────────────────────────────────────────

    private PageSummary summarisePage(String pageText, int pageNum) {
        try {
            String prompt = """
                    Summarise the following page content.
                    
                    === PAGE TEXT ===
                    %s
                    === END PAGE TEXT ===
                    
                    Reply ONLY with the JSON object described in your instructions.
                    """.formatted(pageText);

            String raw = summaryClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("🔍  Raw LLM response page {}: {}", pageNum, raw);

            String cleaned = extractJsonObject(raw);
            if (cleaned == null) {
                log.warn("⚠️  Page {} — could not parse LLM response, using fallback", pageNum);
                return fallback(pageNum);
            }

            RawPageSummary raw2 = objectMapper.readValue(cleaned, RawPageSummary.class);

            return new PageSummary(
                    pageNum,
                    raw2.title(),
                    raw2.summary(),
                    raw2.keyTopics(),
                    sanitizePageType(raw2.pageType()),
                    false
            );

        } catch (Exception e) {
            log.error("❌  Summarisation failed for page {}: {}", pageNum, e.getMessage());
            return fallback(pageNum);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF text extraction (PDFBox) — same pattern as PageQuestionExtractorService
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> extractPagesText(MultipartFile file) throws IOException {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper  = new PDFTextStripper();
            int             numPages  = document.getNumberOfPages();
            for (int i = 1; i <= numPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                pages.add(text != null ? text.trim() : "");
            }
        }
        return pages;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Strips markdown fences and returns the first complete {...} block, or null. */
    private String extractJsonObject(String raw) {
        if (raw == null) return null;
        String s     = raw.replaceAll("(?s)```json|```", "").trim();
        int    start = s.indexOf('{');
        int    end   = s.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return null;
        return s.substring(start, end + 1);
    }

    /** Hard-truncates page text to stay within LLM context limits. */
    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[... truncated ...]";
    }

    /** Normalises the LLM's pageType value to one of our allowed strings. */
    private String sanitizePageType(String raw) {
        if (raw == null) return "content";
        return switch (raw.trim().toLowerCase()) {
            case "cover"        -> "cover";
            case "instructions" -> "instructions";
            case "questions"    -> "questions";
            case "answer-key"   -> "answer-key";
            case "blank"        -> "blank";
            default             -> "content";   // safe fallback for anything unexpected
        };
    }

    /** Returned when the LLM response cannot be parsed. */
    private PageSummary fallback(int pageNum) {
        return new PageSummary(
                pageNum,
                "Page " + pageNum,
                "Summary could not be generated for this page.",
                null,
                "content",
                false
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal DTO for JSON parsing
    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawPageSummary(
            String title,
            String summary,
            String keyTopics,
            String pageType
    ) {}
}