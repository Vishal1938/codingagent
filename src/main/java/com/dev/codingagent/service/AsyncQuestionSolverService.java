package com.dev.codingagent.service;

import com.dev.codingagent.dto.JobStore;
import com.dev.codingagent.dto.QuestionAnswer;
import com.dev.codingagent.dto.SolverJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    public AsyncQuestionSolverService(
            DocumentParserService documentParser,
            QuestionExtractorService questionExtractor,
            AnswerGeneratorService answerGenerator,
            PdfGeneratorService pdfGenerator,
            EmailService emailService,
            JobStore jobStore) {
        this.documentParser = documentParser;
        this.questionExtractor = questionExtractor;
        this.answerGenerator = answerGenerator;
        this.pdfGenerator = pdfGenerator;
        this.emailService = emailService;
        this.jobStore = jobStore;
    }

    @Async
    public void processAsync(SolverJob job, byte[] fileBytes,
                             String fileName, String systemPrompt, String email) {
        log.info("═══════════════════════════════════════");
        log.info("🚀  Async job started: {}", job.getJobId());
        job.markProcessing();
        jobStore.save(job);

        try {
            // Step 1 — Parse
            log.info("📌  [{}] Step 1/3 Parsing...", job.getJobId());
            String text = documentParser.extractTextFromBytes(fileBytes, fileName);

            // Step 2 — Extract questions
            log.info("📌  [{}] Step 2/3 Extracting questions...", job.getJobId());
            List<QuestionExtractorService.ExtractedQuestion> questions =
                    questionExtractor.extractQuestions(text);

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

            // Step 5 — Send email if provided
            if (email != null && !email.isBlank()) {
                log.info("📧  Sending email to: {}", email);
                emailService.sendResultEmail(email, job.getJobId(), fileName, pdfPath);
            }

        } catch (Exception e) {
            log.error("❌  Job {} failed: {}", job.getJobId(), e.getMessage(), e);
            job.markFailed(e.getMessage());
            jobStore.save(job);
        }
    }
}