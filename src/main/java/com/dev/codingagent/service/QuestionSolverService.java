package com.dev.codingagent.service;

import com.dev.codingagent.dto.ExtractedQuestion;
import com.dev.codingagent.dto.QuestionAnswer;
import com.dev.codingagent.dto.QuestionSolverResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@Service
public class QuestionSolverService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSolverService.class);

    private final DocumentParserService documentParser;
    private final QuestionExtractorService questionExtractor;
    private final AnswerGeneratorService answerGenerator;
    private final PageQuestionExtractorService pageQuestionExtractorService;
    public QuestionSolverService(
            DocumentParserService documentParser,
            QuestionExtractorService questionExtractor,
            AnswerGeneratorService answerGenerator,PageQuestionExtractorService pageQuestionExtractorService) {
        this.documentParser = documentParser;
        this.questionExtractor = questionExtractor;
        this.answerGenerator = answerGenerator;
        this.pageQuestionExtractorService=pageQuestionExtractorService;
    }

    public QuestionSolverResponse solve(MultipartFile file, String systemPrompt) throws IOException {
        long start = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();

        log.info("═══════════════════════════════════════════════");
        log.info("🚀  Question Solver started for: {}", fileName);
        log.info("═══════════════════════════════════════════════");

        // ── Step 1: Parse document ─────────────────────────────
        log.info("📌  Step 1/3 — Parsing document...");
        String documentText = documentParser.extractText(file);

        // ── Step 2: Extract questions ──────────────────────────
        log.info("📌  Step 2/3 — Extracting questions...");
//        List<ExtractedQuestion> questions =
//                questionExtractor.extractQuestions(documentText);
        List<ExtractedQuestion> questions= pageQuestionExtractorService.extractFromPdf(file).allQuestions();


        if (questions.isEmpty()) {
            log.warn("⚠️  No questions found in document");
            return new QuestionSolverResponse(fileName, 0,
                    System.currentTimeMillis() - start, List.of());
        }

        log.info("✅  Found {} questions", questions.size());

        // ── Step 3: Generate answers ───────────────────────────
        log.info("📌  Step 3/3 — Generating answers...");
        List<QuestionAnswer> answers = answerGenerator.generateAnswers(questions, systemPrompt);

        long elapsed = System.currentTimeMillis() - start;
        log.info("═══════════════════════════════════════════════");
        log.info("✅  Question Solver completed in {}ms", elapsed);
        log.info("📊  {} questions solved", answers.size());
        log.info("═══════════════════════════════════════════════");

        return new QuestionSolverResponse(fileName, answers.size(), elapsed, answers);
    }
}