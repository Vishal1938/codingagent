package com.dev.codingagent.service;

import com.dev.codingagent.dto.*;
import com.dev.codingagent.entity.JobResult;
import com.dev.codingagent.repository.JobResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class AsyncQuestionSolverService {

    private static final Logger log = LoggerFactory.getLogger(AsyncQuestionSolverService.class);

    private final DocumentParserService        documentParser;
    private final QuestionExtractorService     questionExtractor;
    private final AnswerGeneratorService       answerGenerator;
    private final PdfGeneratorService          pdfGenerator;
    private final EmailService                 emailService;
    private final JobStore                     jobStore;
    private final PageQuestionExtractorService pageQuestionExtractorService;
    private final JobResultRepository          jobResultRepository;  // ← NEW

    public AsyncQuestionSolverService(
            DocumentParserService documentParser,
            QuestionExtractorService questionExtractor,
            AnswerGeneratorService answerGenerator,
            PdfGeneratorService pdfGenerator,
            EmailService emailService,
            JobStore jobStore,
            PageQuestionExtractorService pageQuestionExtractorService,
            JobResultRepository jobResultRepository) {
        this.documentParser              = documentParser;
        this.questionExtractor           = questionExtractor;
        this.answerGenerator             = answerGenerator;
        this.pdfGenerator                = pdfGenerator;
        this.emailService                = emailService;
        this.jobStore                    = jobStore;
        this.pageQuestionExtractorService = pageQuestionExtractorService;
        this.jobResultRepository         = jobResultRepository;
    }

    // ── userEmail added — needed to persist result per user ───────────────
    @Async
    public void processAsync(SolverJob job, byte[] fileBytes,
                             String fileName, String systemPrompt,
                             String email, String userEmail) {
        log.info("═══════════════════════════════════════");
        log.info("🚀  Async job started: {}", job.getJobId());

        long start = System.currentTimeMillis();

        // Persist PROCESSING record immediately so user sees it in history
        JobResult jobResult = new JobResult(job.getJobId(), userEmail, fileName);
        jobResultRepository.save(jobResult);

        job.markProcessing();
        jobStore.save(job);

        try {
            // Reconstruct MultipartFile from bytes — request-scoped file is gone
            MultipartFile wrappedFile = new MockMultipartFile(
                    "file", fileName, "application/pdf", fileBytes);

            // Step 1 — Parse
            log.info("📌  [{}] Step 1/3 Parsing...", job.getJobId());
            String text = documentParser.extractTextFromBytes(fileBytes, fileName);

            // Step 2 — Extract questions
            log.info("📌  [{}] Step 2/3 Extracting questions...", job.getJobId());
            DocumentExtractionResponse extractionResponse =
                    pageQuestionExtractorService.extractFromPdf(wrappedFile);
            List<ExtractedQuestion> questions = extractionResponse.allQuestions();
            log.info("📌  [{}] Extracted {} questions from {} pages",
                    job.getJobId(), questions.size(), extractionResponse.totalPages());

            // Step 3 — Generate answers
            log.info("📌  [{}] Step 3/3 Generating answers...", job.getJobId());
            List<QuestionAnswer> answers =
                    answerGenerator.generateAnswers(questions, systemPrompt);

            // Step 4 — Generate PDF
            log.info("📌  [{}] Generating PDF...", job.getJobId());
            String pdfPath = pdfGenerator.generatePdf(job.getJobId(), fileName, answers);

            long elapsed = System.currentTimeMillis() - start;

            // Mark in-memory job done
            job.markDone(pdfPath);
            jobStore.save(job);

            // ── Persist to MongoDB ─────────────────────────────────────────
            jobResult.markDone(pdfPath, elapsed, questions.size());
            jobResultRepository.save(jobResult);
            log.info("✅  Job {} completed and persisted for user: {}",
                    job.getJobId(), userEmail);

            // Step 5 — Email (best-effort — never fail the job)
            if (email != null && !email.isBlank()) {
                try {
                    log.info("📧  Sending email to: {}", email);
                    emailService.sendResultEmail(email, job.getJobId(), fileName, pdfPath);
                    log.info("📧  Email sent successfully to: {}", email);
                } catch (Exception e) {
                    log.error("📧  Email failed (non-fatal): {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌  Job {} failed: {}", job.getJobId(), e.getMessage(), e);

            job.markFailed(e.getMessage());
            jobStore.save(job);

            // Persist failure so user sees it in history
            jobResult.markFailed(e.getMessage());
            jobResultRepository.save(jobResult);
        }
    }
}