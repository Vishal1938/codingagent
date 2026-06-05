package com.dev.codingagent.papers.service;


import com.dev.codingagent.papers.entity.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tags extracted questions with subject-aware topic + difficulty using an LLM.
 *
 * Strategy:
 *  - Batched: 10 questions per LLM call (lower cost, faster)
 *  - Constrained: LLM must pick topic from TopicTaxonomy for the given subject
 *  - Resilient: parse errors fall back to "General" topic + "medium" difficulty
 */
@Service
public class QuestionTaggingService {

    private static final Logger log = LoggerFactory.getLogger(QuestionTaggingService.class);
    private static final int    BATCH_SIZE = 10;

    private static final String SYSTEM_PROMPT = """
            You are a curriculum tagger. For each question provided, classify it.
            
            You MUST pick the topic from the allowed list for the given subject.
            You MUST pick difficulty from: easy, medium, hard.
            
            Return STRICT JSON array. No prose, no markdown, no code fences.
            
            Format:
            [
              {"index": 0, "topic": "Algebra", "difficulty": "easy"},
              {"index": 1, "topic": "Geometry", "difficulty": "medium"}
            ]
            """;

    // Match a JSON array — handles LLM responses with stray prose/fences
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final ChatClient chatClient;

    public QuestionTaggingService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * Tag every question in the list with topic + difficulty.
     * Mutates the questions in place. Returns the same list for chaining.
     */
    public List<Question> tagAll(List<Question> questions, String subject) {
        if (questions == null || questions.isEmpty()) return questions;

        List<String> allowedTopics = TopicTaxonomy.topicsFor(subject);
        log.info("🏷️  Tagging {} questions for subject={}, {} topics allowed",
                questions.size(), subject, allowedTopics.size());

        // Process in batches
        for (int start = 0; start < questions.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, questions.size());
            List<Question> batch = questions.subList(start, end);

            try {
                tagBatch(batch, subject, allowedTopics);
            } catch (Exception e) {
                log.error("⚠️  Tagging batch {}-{} failed: {} — using defaults",
                        start, end, e.getMessage());
                applyDefaults(batch);
            }
        }
        return questions;
    }

    // ── Internal ────────────────────────────────────────────────

    private void tagBatch(List<Question> batch, String subject, List<String> allowedTopics) {

        // Build user prompt: subject + allowed topics + numbered question list
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Subject: ").append(subject).append("\n");
        userPrompt.append("Allowed topics: ").append(String.join(", ", allowedTopics)).append("\n\n");
        userPrompt.append("Questions:\n");
        for (int i = 0; i < batch.size(); i++) {
            userPrompt.append(i).append(". ")
                    .append(truncate(batch.get(i).getText(), 300))
                    .append("\n");
        }

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt.toString())
                .call()
                .content();

        log.debug("🏷️  Raw LLM response: {}", response);

        // Extract JSON array even if LLM wrapped it in prose/markdown
        Matcher matcher = JSON_ARRAY.matcher(response);
        if (!matcher.find()) {
            log.warn("⚠️  No JSON array in response — using defaults for batch");
            applyDefaults(batch);
            return;
        }

        String json = matcher.group();
        List<TagResult> tags = parseTagResults(json);

        // Apply tags by index
        for (TagResult t : tags) {
            if (t.index >= 0 && t.index < batch.size()) {
                Question q = batch.get(t.index);
                // Validate topic is in allowed list — fall back to "General" otherwise
                String topic = allowedTopics.contains(t.topic) ? t.topic : "General";
                String difficulty = isValidDifficulty(t.difficulty) ? t.difficulty : "medium";
                q.setTopic(topic);
                q.setDifficulty(difficulty);
                q.setSubject(subject);
            }
        }
    }

    /** Very small JSON parser — only extracts what we need. */
    private List<TagResult> parseTagResults(String json) {
        List<TagResult> results = new ArrayList<>();
        // Match each object inside the array
        Pattern objPattern = Pattern.compile(
                "\\{\\s*\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"topic\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"difficulty\"\\s*:\\s*\"([^\"]+)\"\\s*}");
        Matcher m = objPattern.matcher(json);
        while (m.find()) {
            results.add(new TagResult(
                    Integer.parseInt(m.group(1)),
                    m.group(2),
                    m.group(3).toLowerCase()
            ));
        }
        return results;
    }

    private void applyDefaults(List<Question> batch) {
        for (Question q : batch) {
            if (q.getTopic() == null)      q.setTopic("General");
            if (q.getDifficulty() == null) q.setDifficulty("medium");
        }
    }

    private boolean isValidDifficulty(String d) {
        return "easy".equals(d) || "medium".equals(d) || "hard".equals(d);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // Internal record for parsed tag result
    private record TagResult(int index, String topic, String difficulty) {}
}