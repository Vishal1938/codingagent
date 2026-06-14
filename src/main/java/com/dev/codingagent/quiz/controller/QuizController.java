package com.dev.codingagent.quiz.controller;

import com.dev.codingagent.quiz.dto.*;
import com.dev.codingagent.quiz.repository.QuizQuestionRepository;
import com.dev.codingagent.quiz.repository.QuizRepository;
import com.dev.codingagent.quiz.repository.QuizResultRepository;
import com.dev.codingagent.quiz.service.QuizGenerationService;
import com.dev.codingagent.quiz.service.QuizGradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quiz endpoints — PR-1: generate (async), status poll, archive list, delete.
 * Attempt + submit + result land in PR-2.
 */
@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final QuizGenerationService  generationService;
    private final QuizJobStore           jobStore;
    private final QuizRepository         quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizResultRepository   quizResultRepository;
    private final QuizGradingService quizGradingService;

    public QuizController(QuizGenerationService generationService,
                          QuizJobStore jobStore,
                          QuizRepository quizRepository,
                          QuizQuestionRepository quizQuestionRepository,
                          QuizResultRepository quizResultRepository,QuizGradingService quizGradingService) {
        this.generationService      = generationService;
        this.jobStore               = jobStore;
        this.quizRepository         = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizResultRepository   = quizResultRepository;
        this.quizGradingService =quizGradingService;
    }

    /** Upload an MCQ paper → async extraction. Returns a jobId to poll. */
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @AuthenticationPrincipal UserDetails user) {

        log.info("📡  POST /api/quizzes/generate | {} | user: {}",
                file.getOriginalFilename(), user.getUsername());

        try {
            // Read bytes eagerly (request-scoped multipart dies after return)
            byte[] bytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            String jobId = UUID.randomUUID().toString();
            QuizJob job = new QuizJob(jobId, fileName);
            jobStore.put(job);

            generationService.generateAsync(jobId, bytes, fileName, title, user.getUsername());

            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "status", "PENDING",
                    "message", "Extracting MCQs. Poll /status/" + jobId
            ));
        } catch (Exception e) {
            log.error("❌  Quiz generate failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start: " + e.getMessage()));
        }
    }

    /** Poll extraction status. */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> status(@PathVariable String jobId) {
        QuizJob job = jobStore.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "jobId", job.getJobId(),
                "status", job.getStatus(),
                "quizId", job.getQuizId() == null ? "" : job.getQuizId(),
                "questionCount", job.getQuestionCount() == null ? 0 : job.getQuestionCount(),
                "error", job.getError() == null ? "" : job.getError()
        ));
    }

    /** User's quiz archive. */
    @GetMapping("/my")
    public ResponseEntity<List<QuizSummaryDto>> myQuizzes(
            @AuthenticationPrincipal UserDetails user) {
        List<QuizSummaryDto> quizzes = quizRepository
                .findByUserIdOrderByCreatedAtDesc(user.getUsername())
                .stream().map(QuizSummaryDto::from).toList();
        return ResponseEntity.ok(quizzes);
    }

    /** Fetch a quiz for attempting — WITHOUT correct answers (anti-cheat). */
    @GetMapping("/{quizId}/attempt")
    public ResponseEntity<?> getForAttempt(@PathVariable String quizId,
                                           @AuthenticationPrincipal UserDetails user) {
        return quizRepository.findByQuizIdAndUserId(quizId, user.getUsername())
                .map(quiz -> {
                    // Block re-attempting a completed quiz
                    if ("COMPLETED".equals(quiz.getStatus())) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Quiz already completed — view the review instead"));
                    }
                    var questions = quizQuestionRepository
                            .findByQuizIdOrderByQuestionNumberAsc(quizId);
                    return ResponseEntity.ok(QuizAttemptDto.from(quiz, questions));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Quiz not found")));
    }


    /** Delete a quiz (and its questions + any result). */
    @DeleteMapping("/{quizId}")
    public ResponseEntity<?> delete(@PathVariable String quizId,
                                    @AuthenticationPrincipal UserDetails user) {
        return quizRepository.findByQuizIdAndUserId(quizId, user.getUsername())
                .map(quiz -> {
                    quizQuestionRepository.deleteByQuizId(quizId);
                    quizResultRepository.deleteByQuizId(quizId);
                    quizRepository.deleteByQuizId(quizId);
                    log.info("🗑️  Deleted quiz {}", quizId);
                    return ResponseEntity.ok(Map.of("message", "Quiz deleted"));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Quiz not found")));
    }


    /** Submit answers → instant grade → full result with reveal + analytics. */
    @PostMapping("/{quizId}/submit")
    public ResponseEntity<?> submit(@PathVariable String quizId,
                                    @RequestBody QuizSubmitRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        log.info("📡  POST /api/quizzes/{}/submit | user: {}", quizId, user.getUsername());
        try {
            QuizResultDto result = quizGradingService.submitAndGrade(quizId, user.getUsername(), request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Fetch the result of a completed quiz (review anytime). */
    @GetMapping("/{quizId}/result")
    public ResponseEntity<?> result(@PathVariable String quizId,
                                    @AuthenticationPrincipal UserDetails user) {
        try {
            QuizResultDto result = quizGradingService.getResult(quizId, user.getUsername());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}