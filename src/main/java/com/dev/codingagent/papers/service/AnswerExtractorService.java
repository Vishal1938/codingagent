package com.dev.codingagent.papers.service;



import com.dev.codingagent.solver.dto.ExtractedQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts ANSWERS from an already-generated Q&A document text.
 *
 * Unlike AnswerGeneratorService (which SOLVES each question from scratch
 * with one LLM call per question), this service PAIRS each known question
 * with its already-written answer found in the document text.
 *
 * Use this when the source PDF already contains both questions and answers
 * (e.g. a user's previously generated report being shared to the community).
 * It's much cheaper: batched extraction instead of N solve calls.
 */
@Service
public class AnswerExtractorService {

    private static final Logger log = LoggerFactory.getLogger(AnswerExtractorService.class);
    private static final int BATCH_SIZE = 5;   // questions per LLM call

    private static final String SYSTEM_PROMPT = """
            You are an answer-extraction assistant. You are given the full text of a
            document that ALREADY CONTAINS questions and their worked answers, plus a
            list of specific questions.

            For each question in the list, find its corresponding answer in the document
            text and return that answer EXACTLY as written — including any LaTeX math
            (\\( \\), \\[ \\], \\text{}, subscripts, etc). Do not re-solve, summarize, or
            shorten. Copy the answer text faithfully.

            If a question's answer cannot be found in the document, return an empty string
            for that question's answer.

            Return STRICT JSON array, no prose, no markdown fences:
            [
              {"index": 0, "answer": "the full answer text for question 0"},
              {"index": 1, "answer": "the full answer text for question 1"}
            ]
            """;

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);
    // Match each {"index": N, "answer": "..."} — DOTALL so answers can span lines
    private static final Pattern OBJ_PATTERN = Pattern.compile(
            "\\{\\s*\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"answer\"\\s*:\\s*\"(.*?)\"\\s*}",
            Pattern.DOTALL);

    private final ChatClient chatClient;

    public AnswerExtractorService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * For each question, find its answer in the document text.
     * Returns a list of answer strings index-aligned with the input questions.
     * Missing answers come back as empty strings.
     *
     * @param documentText full extracted text of the Q&A PDF
     * @param questions    the questions whose answers we want to locate
     */
    public List<String> extractAnswers(String documentText, List<ExtractedQuestion> questions) {
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) answers.add("");  // pre-fill

        if (documentText == null || documentText.isBlank() || questions.isEmpty()) {
            log.warn("⚠️  No document text or no questions — returning empty answers");
            return answers;
        }

        log.info("📝  Extracting answers for {} questions from {} chars of text",
                questions.size(), documentText.length());

        for (int start = 0; start < questions.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, questions.size());
            List<ExtractedQuestion> batch = questions.subList(start, end);

            try {
                extractBatch(documentText, batch, start, answers);
            } catch (Exception e) {
                log.error("⚠️  Answer extraction batch {}-{} failed: {}",
                        start, end, e.getMessage());
                // leave those answers as empty strings — non-fatal
            }
        }

        long found = answers.stream().filter(a -> a != null && !a.isBlank()).count();
        log.info("📝  Extracted {}/{} answers", found, questions.size());
        return answers;
    }

    private void extractBatch(String documentText, List<ExtractedQuestion> batch,
                              int offset, List<String> answers) {

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("DOCUMENT TEXT:\n");
        userPrompt.append(documentText).append("\n\n");
        userPrompt.append("QUESTIONS (find each answer in the document above):\n");
        for (int i = 0; i < batch.size(); i++) {
            userPrompt.append(i).append(". ").append(batch.get(i).question()).append("\n");
        }

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt.toString())
                .call()
                .content();

        Matcher arrMatch = JSON_ARRAY.matcher(response);
        if (!arrMatch.find()) {
            log.warn("⚠️  No JSON array in answer-extraction response for batch at offset {}", offset);
            return;
        }

        String json = arrMatch.group();
        Matcher m = OBJ_PATTERN.matcher(json);
        while (m.find()) {
            int localIndex = Integer.parseInt(m.group(1));
            String answer  = unescapeJson(m.group(2));
            int globalIndex = offset + localIndex;
            if (globalIndex >= 0 && globalIndex < answers.size()) {
                answers.set(globalIndex, answer);
            }
        }
    }

    /** Minimal JSON string unescaping for the answer payloads. */
    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}