package com.dev.codingagent.service;

import com.dev.codingagent.dto.*;
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

    private final DocumentParserService documentParser;
    private final QuestionExtractorService questionExtractor;
    private final AnswerGeneratorService answerGenerator;
    private final PdfGeneratorService pdfGenerator;
    private final EmailService emailService;
    private final JobStore jobStore;
    private PageQuestionExtractorService pageQuestionExtractorService;

    public AsyncQuestionSolverService(
            DocumentParserService documentParser,
            QuestionExtractorService questionExtractor,
            AnswerGeneratorService answerGenerator,
            PdfGeneratorService pdfGenerator,
            EmailService emailService,
            JobStore jobStore,PageQuestionExtractorService pageQuestionExtractorService) {
        this.documentParser = documentParser;
        this.questionExtractor = questionExtractor;
        this.answerGenerator = answerGenerator;
        this.pdfGenerator = pdfGenerator;
        this.emailService = emailService;
        this.jobStore = jobStore;
        this.pageQuestionExtractorService=pageQuestionExtractorService;
    }

    @Async
    public void processAsync(SolverJob job, byte[] fileBytes,
                             String fileName, String systemPrompt, String email) {
        log.info("═══════════════════════════════════════");
        log.info("🚀  Async job started: {}", job.getJobId());
        job.markProcessing();
        jobStore.save(job);

        try {
            // Step 1 — reconstruct MultipartFile from bytes (safe in async context)
            MultipartFile wrappedFile = new MockMultipartFile(
                    "file", fileName, "application/pdf", fileBytes
            );
            // Step 1 — Parse
            log.info("📌  [{}] Step 1/3 Parsing...", job.getJobId());
            String text = documentParser.extractTextFromBytes(fileBytes, fileName);

            // Step 2 — Extract questions
            log.info("📌  [{}] Step 2/3 Extracting questions...", job.getJobId());
//            List<ExtractedQuestion> questions =
//                    questionExtractor.extractQuestions(text);
            //TODO changes
            // Step 2 — Extract questions
            log.info("📌  [{}] Step 2/3 Extracting questions...", job.getJobId());
            DocumentExtractionResponse extractionResponse = pageQuestionExtractorService.extractFromPdf(wrappedFile);
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

            job.markDone(pdfPath);
            jobStore.save(job);
            log.info("✅  Job {} completed", job.getJobId());

            // Step 5 — Send email (best-effort — don't fail the job if this fails)
            if (email != null && !email.isBlank()) {
                try {
                    log.info("📧  Sending email to: {}", email);
                    emailService.sendResultEmail(email, job.getJobId(), fileName, pdfPath);
                    log.info("📧  Email sent successfully to: {}", email);
                } catch (Exception e) {
                    // Email failed — log it but don't mark job as failed
                    // User can still download the PDF directly
                    log.error("📧  Email failed (Railway blocks SMTP 587): {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌  Job {} failed: {}", job.getJobId(), e.getMessage(), e);
            job.markFailed(e.getMessage());
            jobStore.save(job);
        }
    }
}