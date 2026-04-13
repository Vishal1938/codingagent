package com.dev.codingagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionExtractorService {

    private static final Logger log = LoggerFactory.getLogger(QuestionExtractorService.class);
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionExtractorService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a question extraction expert.
                        Your job is to read a document and extract ALL questions from it.
                        You must respond ONLY with a valid JSON array — no explanation, no markdown, no preamble.
                        Each item in the array must have exactly these fields:
                        {
                          "id": <number starting from 1>,
                          "question": "<the full question text>",
                          "type": "<one of: MCQ, short_answer, descriptive, numerical>"
                        }
                        If no questions are found, return an empty array: []
                        """)
                .build();
    }

    public List<ExtractedQuestion> extractQuestions(String documentText) {
        log.info("🔍  Extracting questions from document text ({} chars)...", documentText.length());

        String prompt = """
                Extract all questions from the following document text.
                Return ONLY a JSON array as instructed.

                Document:
                ---
                %s
                ---
                """.formatted(documentText);

        String rawResponse = chatClient.prompt()
                .user(prompt)
                .call().content();

        log.info("📨  Raw LLM response received ({} chars)", rawResponse != null ? rawResponse.length() : 0);

        return parseQuestions(rawResponse);
    }

    private List<ExtractedQuestion> parseQuestions(String rawResponse) {
        try {
            // Clean up response — remove markdown fences if model adds them
            String cleaned = rawResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            // Find the JSON array boundaries
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');

            if (start == -1 || end == -1) {
                log.warn("⚠️  No JSON array found in LLM response, returning empty list");
                return new ArrayList<>();
            }

            String jsonArray = cleaned.substring(start, end + 1);
            List<ExtractedQuestion> questions = objectMapper.readValue(
                    jsonArray,
                    new TypeReference<List<ExtractedQuestion>>() {}
            );

            log.info("✅  Extracted {} questions successfully", questions.size());
            return questions;

        } catch (Exception e) {
            log.error("❌  Failed to parse questions JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // Inner record matching LLM JSON output
    public record ExtractedQuestion(int id, String question, String type) {}
}