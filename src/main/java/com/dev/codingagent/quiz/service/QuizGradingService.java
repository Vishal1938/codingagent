package com.dev.codingagent.quiz.service;

import com.dev.codingagent.quiz.dto.QuizResultDto;
import com.dev.codingagent.quiz.dto.QuizSubmitRequest;
import com.dev.codingagent.quiz.entity.Quiz;
import com.dev.codingagent.quiz.entity.QuizQuestion;
import com.dev.codingagent.quiz.entity.QuizResult;
import com.dev.codingagent.quiz.repository.QuizQuestionRepository;
import com.dev.codingagent.quiz.repository.QuizRepository;
import com.dev.codingagent.quiz.repository.QuizResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Grades a quiz submission instantly (MCQ auto-grading — no LLM).
 * One attempt only: rejects if already COMPLETED.
 * Stores a QuizResult and updates the Quiz with final score.
 */
@Service
public class QuizGradingService {

    private static final Logger log = LoggerFactory.getLogger(QuizGradingService.class);

    private final QuizRepository         quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizResultRepository   quizResultRepository;

    public QuizGradingService(QuizRepository quizRepository,
                              QuizQuestionRepository quizQuestionRepository,
                              QuizResultRepository quizResultRepository) {
        this.quizRepository         = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizResultRepository   = quizResultRepository;
    }

    /**
     * Grade a submission and return the full result with reveal + analytics.
     * @throws IllegalStateException if quiz not found/owned, or already completed.
     */
    public QuizResultDto submitAndGrade(String quizId, String userEmail,
                                        QuizSubmitRequest request) {

        Quiz quiz = quizRepository.findByQuizIdAndUserId(quizId, userEmail)
                .orElseThrow(() -> new IllegalStateException("Quiz not found or not yours"));

        if ("COMPLETED".equals(quiz.getStatus())) {
            throw new IllegalStateException("This quiz has already been completed");
        }

        List<QuizQuestion> questions =
                quizQuestionRepository.findByQuizIdOrderByQuestionNumberAsc(quizId);

        // Map submitted answers by questionId for quick lookup
        Map<String, Integer> submitted = new HashMap<>();
        if (request.answers() != null) {
            for (QuizSubmitRequest.SubmittedAnswer a : request.answers()) {
                submitted.put(a.quizQuestionId(), a.selectedOption());
            }
        }

        // Grade each question
        int correctCount = 0;
        List<QuizResult.AnswerRecord> records = new ArrayList<>();
        List<QuizResultDto.ReviewQuestion> review = new ArrayList<>();
        Map<String, int[]> topicTally = new LinkedHashMap<>();  // topic → [correct, total]

        for (QuizQuestion q : questions) {
            Integer selected = submitted.get(q.getQuizQuestionId());
            boolean isCorrect = selected != null
                    && q.getCorrectOption() != null
                    && selected.equals(q.getCorrectOption());
            if (isCorrect) correctCount++;

            // Record for storage (analytics)
            records.add(new QuizResult.AnswerRecord(
                    q.getQuizQuestionId(), selected, isCorrect, q.getTopic()));

            // Review item (full reveal)
            review.add(new QuizResultDto.ReviewQuestion(
                    q.getQuizQuestionId(), q.getQuestionNumber(), q.getText(),
                    q.getOptions(), selected, q.getCorrectOption(), isCorrect,
                    q.getExplanation(), q.getTopic(), q.getAnswerConfident()));

            // Topic tally
            String topic = (q.getTopic() == null || q.getTopic().isBlank())
                    ? "General" : q.getTopic();
            topicTally.putIfAbsent(topic, new int[]{0, 0});
            topicTally.get(topic)[1]++;
            if (isCorrect) topicTally.get(topic)[0]++;
        }

        int total = questions.size();
        double score = total > 0 ? (100.0 * correctCount / total) : 0.0;

        // Persist QuizResult
        QuizResult result = new QuizResult();
        result.setQuizId(quizId);
        result.setUserId(userEmail);
        result.setAnswers(records);
        result.setScore(score);
        result.setCorrectCount(correctCount);
        result.setTotalQuestions(total);
        result.setTimeTakenSec(request.timeTakenSec());
        result.setSubmittedAt(LocalDateTime.now());
        quizResultRepository.save(result);

        // Update Quiz → COMPLETED
        quiz.setStatus("COMPLETED");
        quiz.setScore(score);
        quiz.setCorrectCount(correctCount);
        quiz.setTimeTakenSec(request.timeTakenSec());
        quiz.setCompletedAt(LocalDateTime.now());
        quizRepository.save(quiz);

        log.info("✅  [Quiz {}] graded — {}/{} ({}%)", quizId, correctCount, total, Math.round(score));

        // Build topic breakdown
        List<QuizResultDto.TopicBreakdown> breakdown = topicTally.entrySet().stream()
                .map(e -> new QuizResultDto.TopicBreakdown(
                        e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();

        return new QuizResultDto(
                quizId, quiz.getTitle(), score, correctCount, total,
                request.timeTakenSec(), review, breakdown);
    }

    /**
     * Fetch a previously completed quiz's result for review.
     */
    public QuizResultDto getResult(String quizId, String userEmail) {
        Quiz quiz = quizRepository.findByQuizIdAndUserId(quizId, userEmail)
                .orElseThrow(() -> new IllegalStateException("Quiz not found or not yours"));

        if (!"COMPLETED".equals(quiz.getStatus())) {
            throw new IllegalStateException("This quiz hasn't been completed yet");
        }

        QuizResult result = quizResultRepository.findByQuizIdAndUserId(quizId, userEmail)
                .orElseThrow(() -> new IllegalStateException("No result found"));

        List<QuizQuestion> questions =
                quizQuestionRepository.findByQuizIdOrderByQuestionNumberAsc(quizId);

        // Map stored answers by questionId
        Map<String, QuizResult.AnswerRecord> answerMap = new HashMap<>();
        if (result.getAnswers() != null) {
            for (QuizResult.AnswerRecord r : result.getAnswers()) {
                answerMap.put(r.getQuizQuestionId(), r);
            }
        }

        List<QuizResultDto.ReviewQuestion> review = new ArrayList<>();
        Map<String, int[]> topicTally = new LinkedHashMap<>();

        for (QuizQuestion q : questions) {
            QuizResult.AnswerRecord r = answerMap.get(q.getQuizQuestionId());
            Integer selected = r != null ? r.getSelectedOption() : null;
            boolean isCorrect = r != null && Boolean.TRUE.equals(r.getIsCorrect());

            review.add(new QuizResultDto.ReviewQuestion(
                    q.getQuizQuestionId(), q.getQuestionNumber(), q.getText(),
                    q.getOptions(), selected, q.getCorrectOption(), isCorrect,
                    q.getExplanation(), q.getTopic(), q.getAnswerConfident()));

            String topic = (q.getTopic() == null || q.getTopic().isBlank())
                    ? "General" : q.getTopic();
            topicTally.putIfAbsent(topic, new int[]{0, 0});
            topicTally.get(topic)[1]++;
            if (isCorrect) topicTally.get(topic)[0]++;
        }

        List<QuizResultDto.TopicBreakdown> breakdown = topicTally.entrySet().stream()
                .map(e -> new QuizResultDto.TopicBreakdown(
                        e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();

        return new QuizResultDto(
                quizId, quiz.getTitle(), result.getScore(),
                result.getCorrectCount(), result.getTotalQuestions(),
                result.getTimeTakenSec(), review, breakdown);
    }
}