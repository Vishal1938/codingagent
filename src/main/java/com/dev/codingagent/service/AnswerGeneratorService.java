package com.dev.codingagent.service;

import com.dev.codingagent.dto.QuestionAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AnswerGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AnswerGeneratorService.class);
    private final ChatClient chatClient;

    @Value("${agent.system.prompt}")
    private String defaultSystemPrompt;

    public AnswerGeneratorService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You are an expert problem solver and educator.
                        When given a question, provide a clear, accurate, and concise answer.
                        Do not repeat the question in your answer.
                        Be direct and factual.
                        """)
                .build();
    }

    public List<QuestionAnswer> generateAnswers(
            List<QuestionExtractorService.ExtractedQuestion> questions,
            String customSystemPrompt) {

        log.info("💡  Generating answers for {} questions...", questions.size());

        return questions.stream()
                .map(q -> solveQuestion(q, customSystemPrompt))
                .toList();
    }

    private QuestionAnswer solveQuestion(
            QuestionExtractorService.ExtractedQuestion question,
            String customSystemPrompt) {

        log.info("❓  Solving Q-{}: {}", question.id(),
                question.question().length() > 60
                        ? question.question().substring(0, 60) + "..."
                        : question.question());

        String systemPrompt = (customSystemPrompt != null && !customSystemPrompt.isBlank())
                ? customSystemPrompt
                : null;

        String prompt = """
                Question type: %s
                Question: %s

                Provide a clear and accurate answer.
                """.formatted(question.type(), question.question());

        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt().user(prompt);
            if (systemPrompt != null) {
                request = chatClient.prompt()
                        .system(systemPrompt)
                        .user(prompt);
            }

            String answer = request.call().content();
            log.info("✅  Q-{} answered ({} chars)", question.id(),
                    answer != null ? answer.length() : 0);

            return new QuestionAnswer(
                    question.id(),
                    question.question(),
                    answer != null ? answer.trim() : "Unable to generate answer",
                    question.type()
            );

        } catch (Exception e) {
            log.error("❌  Failed to answer Q-{}: {}", question.id(), e.getMessage());
            return new QuestionAnswer(
                    question.id(),
                    question.question(),
                    "Error generating answer: " + e.getMessage(),
                    question.type()
            );
        }
    }
}
