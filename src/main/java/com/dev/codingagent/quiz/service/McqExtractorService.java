package com.dev.codingagent.quiz.service;


import com.dev.codingagent.quiz.entity.QuizQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts MCQs from a question paper's text.
 *
 * For each question, pulls: the question text, the options, the correct option
 * index, an explanation, and a topic. Looks for an answer key in the document
 * first; if absent, the LLM determines the correct answer from subject knowledge
 * and flags lower confidence.
 *
 * Batched to keep each LLM call focused and parseable.
 */
@Service
public class McqExtractorService {

    private static final Logger log = LoggerFactory.getLogger(McqExtractorService.class);
    private static final int BATCH_SIZE = 5;   // questions per LLM call

    private static final String SYSTEM_PROMPT = """
            You extract multiple-choice questions (MCQs) from exam paper text.

            For EACH MCQ you find, produce a JSON object with:
              - "number":  the question number (integer)
              - "text":    the full question text
              - "options": array of option strings (the choices, in order)
              - "correct": 0-based index of the correct option
              - "confident": true if you are sure of the correct answer, false if guessing
              - "explanation": one or two sentences on why the correct option is right
              - "topic": a short topic label for the question

            Rules:
            - If the document contains an ANSWER KEY, use it to set "correct" and set confident=true.
            - If there is no key, determine the correct answer yourself from subject knowledge.
              Set confident=false if you are not sure.
            - Preserve any math/LaTeX in the question and options exactly.
            - Return ONLY a strict JSON array, no prose, no markdown fences.

            Format:
            [
              {"number":1,"text":"...","options":["A","B","C","D"],"correct":2,
               "confident":true,"explanation":"...","topic":"..."}
            ]
            """;

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final ChatClient chatClient;

    public McqExtractorService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * Extract all MCQs from the given document text.
     * @param documentText full text of the uploaded paper
     * @param quizId       the quiz these questions belong to
     */
    public List<QuizQuestion> extract(String documentText, String quizId) {
        List<QuizQuestion> result = new ArrayList<>();
        if (documentText == null || documentText.isBlank()) {
            log.warn("📝  [MCQ] empty document text for quiz {}", quizId);
            return result;
        }

        log.info("📝  [MCQ] extracting from {} chars for quiz {}", documentText.length(), quizId);

        // For very large papers we chunk the text to keep prompts manageable.
        // Competitive MCQ papers can be long — chunk by ~8000 char windows.
        List<String> chunks = chunkText(documentText, 8000);
        int questionNumber = 1;

        for (int c = 0; c < chunks.size(); c++) {
            try {
                List<QuizQuestion> batch = extractChunk(chunks.get(c), quizId);
                for (QuizQuestion q : batch) {
                    q.setQuestionNumber(questionNumber++);
                    result.add(q);
                }
                log.info("📝  [MCQ] chunk {}/{} → {} questions", c + 1, chunks.size(), batch.size());
            } catch (Exception e) {
                log.error("⚠️  [MCQ] chunk {} extraction failed: {}", c, e.getMessage());
            }
        }

        log.info("✅  [MCQ] extracted {} questions for quiz {}", result.size(), quizId);
        return result;
    }

    // ── Internal ─────────────────────────────────────────────────

    private List<QuizQuestion> extractChunk(String chunk, String quizId) {
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Extract all MCQs from this text:\n\n" + chunk)
                .call()
                .content();

        Matcher arr = JSON_ARRAY.matcher(response);
        if (!arr.find()) {
            log.warn("⚠️  [MCQ] no JSON array in response");
            return List.of();
        }
        return parseQuestions(arr.group(), quizId);
    }

    /**
     * Parse the JSON array into QuizQuestion objects.
     * Hand-rolled tolerant parsing (the LLM JSON can be slightly irregular).
     */
    private List<QuizQuestion> parseQuestions(String json, String quizId) {
        List<QuizQuestion> questions = new ArrayList<>();

        // Split into top-level objects by matching balanced braces
        List<String> objects = splitJsonObjects(json);
        for (String obj : objects) {
            try {
                QuizQuestion q = new QuizQuestion();
                q.setQuizQuestionId("qq_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                q.setQuizId(quizId);
                q.setText(extractString(obj, "text"));
                q.setOptions(extractStringArray(obj, "options"));
                q.setCorrectOption(extractInt(obj, "correct"));
                q.setAnswerConfident(extractBool(obj, "confident", true));
                q.setExplanation(extractString(obj, "explanation"));
                q.setTopic(extractString(obj, "topic"));

                // Only keep well-formed questions: has text + at least 2 options
                if (q.getText() != null && !q.getText().isBlank()
                        && q.getOptions() != null && q.getOptions().size() >= 2) {
                    // Clamp correctOption into range
                    if (q.getCorrectOption() == null
                            || q.getCorrectOption() < 0
                            || q.getCorrectOption() >= q.getOptions().size()) {
                        q.setCorrectOption(0);
                        q.setAnswerConfident(false);
                    }
                    questions.add(q);
                }
            } catch (Exception e) {
                log.debug("Skipping malformed question object: {}", e.getMessage());
            }
        }
        return questions;
    }

    // ── Tiny JSON helpers (tolerant, regex-based) ────────────────

    private List<String> splitJsonObjects(String json) {
        List<String> objs = new ArrayList<>();
        int depth = 0, start = -1;
        boolean inString = false;
        char prev = 0;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (ch == '{') { if (depth == 0) start = i; depth++; }
                else if (ch == '}') { depth--; if (depth == 0 && start >= 0) { objs.add(json.substring(start, i + 1)); start = -1; } }
            }
            prev = ch;
        }
        return objs;
    }

    private String extractString(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL)
                .matcher(obj);
        return m.find() ? unescape(m.group(1)) : null;
    }

    private Integer extractInt(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(obj);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private Boolean extractBool(String obj, String key, boolean dflt) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(obj);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : dflt;
    }

    private List<String> extractStringArray(String obj, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(obj);
        if (!m.find()) return List.of();
        String inner = m.group(1);
        List<String> items = new ArrayList<>();
        Matcher sm = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL).matcher(inner);
        while (sm.find()) items.add(unescape(sm.group(1)));
        return items;
    }

    private String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\n", "\n").replace("\\\"", "\"")
                .replace("\\t", "\t").replace("\\\\", "\\");
    }

    private List<String> chunkText(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(i + size, text.length())));
        }
        return chunks.isEmpty() ? List.of(text) : chunks;
    }
}