package com.dev.codingagent.quiz.service;

import com.dev.codingagent.quiz.dto.QuizJob;
import com.dev.codingagent.quiz.dto.QuizJobStore;
import com.dev.codingagent.quiz.entity.Quiz;
import com.dev.codingagent.quiz.entity.QuizQuestion;
import com.dev.codingagent.quiz.repository.QuizQuestionRepository;
import com.dev.codingagent.quiz.repository.QuizRepository;
import com.dev.codingagent.solver.service.DocumentParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates async quiz generation from an uploaded MCQ paper:
 *   parse text → extract MCQs (LLM) → persist Quiz + QuizQuestions.
 *
 * Mirrors the solver's async pattern: bytes are read eagerly in the controller,
 * passed here, processed off the request thread, status tracked in QuizJobStore.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);

    private final DocumentParserService documentParser;
    private final McqExtractorService      mcqExtractor;
    private final QuizRepository           quizRepository;
    private final QuizQuestionRepository   quizQuestionRepository;
    private final QuizJobStore             jobStore;

    public QuizGenerationService(DocumentParserService documentParser,
                                 McqExtractorService mcqExtractor,
                                 QuizRepository quizRepository,
                                 QuizQuestionRepository quizQuestionRepository,
                                 QuizJobStore jobStore) {
        this.documentParser         = documentParser;
        this.mcqExtractor           = mcqExtractor;
        this.quizRepository         = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.jobStore               = jobStore;
    }

    @Async
    public void generateAsync(String jobId, byte[] fileBytes, String fileName,
                              String title, String userEmail) {
        QuizJob job = jobStore.get(jobId);
        try {
            job.setStatus("PROCESSING");

            // 1. Extract text from the uploaded paper (OCR fallback handled inside)
            log.info("🧩  [Quiz {}] parsing document {}", jobId, fileName);
            String text = documentParser.extractTextFromBytes(fileBytes, fileName);

            // 2. Create the Quiz shell
            String quizId = "quiz_" + UUID.randomUUID().toString().replace("-", "");
            Quiz quiz = new Quiz();
            quiz.setQuizId(quizId);
            quiz.setUserId(userEmail);
            quiz.setTitle(title != null && !title.isBlank()
                    ? title : stripExtension(fileName));
            quiz.setSourceFileName(fileName);
            quiz.setStatus("NOT_ATTEMPTED");
            quizRepository.save(quiz);
            job.setQuizId(quizId);

            // 3. Extract MCQs (LLM)
            List<QuizQuestion> questions = mcqExtractor.extract(text, quizId);

            if (questions.isEmpty()) {
                // No MCQs found — clean up and fail the job
                quizRepository.deleteByQuizId(quizId);
                job.setStatus("FAILED");
                job.setError("No multiple-choice questions could be detected in this document.");
                log.warn("⚠️  [Quiz {}] no MCQs found", jobId);
                return;
            }

            // 4. Persist questions + finalize quiz
            quizQuestionRepository.saveAll(questions);
            quiz.setQuestionCount(questions.size());
            quizRepository.save(quiz);

            job.setQuestionCount(questions.size());
            job.setStatus("DONE");
            log.info("✅  [Quiz {}] done — {} questions", jobId, questions.size());

        } catch (Exception e) {
            log.error("❌  [Quiz {}] generation failed: {}", jobId, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setError("Generation failed: " + e.getMessage());
        }
    }

    private String stripExtension(String name) {
        if (name == null) return "Quiz";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}