package com.dev.codingagent.papers.service;


import com.dev.codingagent.papers.dto.AdminUploadPaperRequest;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.entity.Question;
import com.dev.codingagent.papers.repository.PaperRepository;
import com.dev.codingagent.papers.repository.QuestionRepository;

import com.dev.codingagent.solver.dto.DocumentExtractionResponse;
import com.dev.codingagent.solver.dto.ExtractedQuestion;
import com.dev.codingagent.solver.dto.QuestionAnswer;
import com.dev.codingagent.solver.service.AnswerGeneratorService;
import com.dev.codingagent.solver.service.CloudinaryStorageService;
import com.dev.codingagent.solver.service.PageQuestionExtractorService;
import com.dev.codingagent.solver.service.PdfGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin upload pipeline for past papers.
 *
 * Key fix: read the multipart bytes ONCE at the start, then use those
 * bytes for everything downstream. Calling getInputStream() multiple
 * times or transferTo() then getInputStream() breaks on Windows because
 * Tomcat may move/delete the temp file under us.
 */
@Service
public class PaperUploadService {

    private static final Logger log = LoggerFactory.getLogger(PaperUploadService.class);

    private final PageQuestionExtractorService extractor;
    private final AnswerGeneratorService answerGenerator;
    private final PdfGeneratorService pdfGenerator;
    private final QuestionTaggingService       tagger;
    private final CloudinaryStorageService storage;
    private final PaperRepository              paperRepository;
    private final QuestionRepository           questionRepository;

    public PaperUploadService(PageQuestionExtractorService extractor,
                              AnswerGeneratorService answerGenerator,
                              PdfGeneratorService pdfGenerator,
                              QuestionTaggingService tagger,
                              CloudinaryStorageService storage,
                              PaperRepository paperRepository,
                              QuestionRepository questionRepository) {
        this.extractor          = extractor;
        this.answerGenerator    = answerGenerator;
        this.pdfGenerator       = pdfGenerator;
        this.tagger             = tagger;
        this.storage            = storage;
        this.paperRepository    = paperRepository;
        this.questionRepository = questionRepository;
    }

    public Paper upload(MultipartFile file,
                        AdminUploadPaperRequest meta,
                        String adminEmail) throws Exception {

        log.info("📚  [Admin upload] {} | by {}", meta.title(), adminEmail);
        long start = System.currentTimeMillis();

        // ── STEP 0: Read bytes ONCE — single source of truth ────────────
        // After this point we never touch the original MultipartFile again.
        byte[] fileBytes     = file.getBytes();
        String originalName  = file.getOriginalFilename();
        log.info("📚  Read {} bytes from upload", fileBytes.length);

        // ── STEP 1: Create Paper shell ─────────────────────────────────
        String paperId = "p_" + UUID.randomUUID().toString().replace("-", "");
        Paper paper = new Paper();
        paper.setPaperId(paperId);
        paper.setTitle(meta.title());
        paper.setBoard(meta.board());
        paper.setClassLevel(meta.classLevel());
        paper.setSubject(meta.subject());
        paper.setYear(meta.year());
        paper.setTotalMarks(meta.totalMarks());
        paper.setDurationMinutes(meta.durationMinutes());
        paper.setSource("official");
        paper.setUploadedBy(adminEmail);
        paper.setIsPublic(true);
        paper.setApprovedAt(LocalDateTime.now());
        paper.setApprovedBy(adminEmail);
        paperRepository.save(paper);

        // ── STEP 2: Save bytes to a temp File for Cloudinary upload ────
        File tempOriginal = writeBytesToTempFile(fileBytes, paperId + "_original.pdf");
        String originalUrl = storage.uploadPdf(tempOriginal, paperId, "papers");
        paper.setOriginalPdfUrl(originalUrl);
        tempOriginal.delete();

        // ── STEP 3: Extract questions ──────────────────────────────────
        // Wrap bytes as a fresh MultipartFile — guaranteed valid stream
        log.info("📚  [{}] Extracting questions...", paperId);
        MultipartFile wrappedFile = new MockMultipartFile(
                "file", originalName, "application/pdf", fileBytes);
        DocumentExtractionResponse extracted = extractor.extractFromPdf(wrappedFile);
        List<ExtractedQuestion> extractedQs  = extracted.allQuestions();
        log.info("📚  [{}] Extracted {} questions", paperId, extractedQs.size());

        // ── STEP 4: Convert to Question entities ───────────────────────
        List<Question> questions   = new ArrayList<>();
        List<String>   questionIds = new ArrayList<>();
        for (ExtractedQuestion eq : extractedQs) {
            String qId = "q_" + UUID.randomUUID().toString().replace("-", "");
            Question q = new Question(qId, eq.question(), eq.type(), paperId);
            q.setBoard(meta.board());
            q.setClassLevel(meta.classLevel());
            q.setYear(meta.year());
            q.setSubject(meta.subject());
            q.setSource("extracted");
            q.setCreatedBy(adminEmail);
            questions.add(q);
            questionIds.add(qId);
        }

        // ── STEP 5: Tag with topic + difficulty via LLM ────────────────
        tagger.tagAll(questions, meta.subject());

        // ── STEP 6: Generate answers ───────────────────────────────────
        log.info("📚  [{}] Generating answers...", paperId);
        String systemPrompt = "You are a " + meta.subject()
                + " expert. Provide exam-ready answers with full steps. Use LaTeX for math.";
        List<QuestionAnswer> answers = answerGenerator.generateAnswers(extractedQs, systemPrompt);

        // ── STEP 7: Generate answer PDF + upload to Cloudinary ─────────
        Map<String, String> pdfData = pdfGenerator.generatePdf(
                paperId, meta.title(), answers, adminEmail);
        String answerPdfPath = pdfData.get("pdfFile");
        String answerPdfUrl  = pdfData.get("publicUrl");

        // Store expected answers on the questions
        for (int i = 0; i < questions.size() && i < answers.size(); i++) {
            questions.get(i).setExpectedAnswer(answers.get(i).answer());
        }

        // ── STEP 8: Persist questions + finalize paper ─────────────────
        questionRepository.saveAll(questions);
        paper.setQuestionIds(questionIds);
        paper.setQuestionCount(questions.size());
        paper.setAnswerPdfUrl(answerPdfUrl);
        paperRepository.save(paper);

        // Cleanup the local answer PDF — it's on Cloudinary now
        try { new File(answerPdfPath).delete(); } catch (Exception ignored) {}

        long elapsed = System.currentTimeMillis() - start;
        log.info("✅  [{}] Paper uploaded successfully in {}ms — {} questions",
                paperId, elapsed, questions.size());

        return paper;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Write byte array to a temp file on disk.
     * Used because Cloudinary's uploader works with a File, not bytes.
     */
    private File writeBytesToTempFile(byte[] bytes, String name) throws Exception {
        File temp = File.createTempFile("paper_", "_" + name);
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            fos.write(bytes);
        }
        return temp;
    }
}