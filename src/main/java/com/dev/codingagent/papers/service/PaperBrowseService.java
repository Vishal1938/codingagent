package com.dev.codingagent.papers.service;

import com.dev.codingagent.papers.dto.*;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.entity.Question;
import com.dev.codingagent.papers.repository.PaperRepository;
import com.dev.codingagent.papers.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-side service for browsing papers.
 * Search with filters, paper detail with questions, filter options,
 * and view/download counter increments.
 */
@Service
public class PaperBrowseService {

    private static final Logger log = LoggerFactory.getLogger(PaperBrowseService.class);
    private static final int DEFAULT_PAGE_SIZE = 24;

    private final PaperRepository    paperRepository;
    private final QuestionRepository questionRepository;

    public PaperBrowseService(PaperRepository paperRepository,
                              QuestionRepository questionRepository) {
        this.paperRepository    = paperRepository;
        this.questionRepository = questionRepository;
    }

    // ── Search with filters ──────────────────────────────────────────
    public PaperSearchResponse search(String board, String classLevel,
                                      String subject, Integer year,
                                      String titleQuery, int page, int size) {

        int pageSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, pageSize,
                Sort.by(Sort.Direction.DESC, "year", "createdAt"));

        // Normalize blank strings to null so the query treats them as "no filter"
        board      = blankToNull(board);
        classLevel = blankToNull(classLevel);
        subject    = blankToNull(subject);

        Page<Paper> result;
        if (titleQuery != null && !titleQuery.isBlank()) {
            // Title search takes precedence — escape regex special chars
            result = paperRepository.searchByTitle(java.util.regex.Pattern.quote(titleQuery), pageable);
        } else {
            result = paperRepository.search(board, classLevel, subject, year, pageable);
        }

        List<PaperSummaryDto> dtos = result.getContent().stream()
                .map(PaperSummaryDto::from)
                .toList();

        return new PaperSearchResponse(
                dtos,
                page,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages(),
                page + 1 < result.getTotalPages()
        );
    }

    // ── Paper detail with questions ──────────────────────────────────
    public Optional<PaperDetailDto> getPaperDetail(String paperId) {
        return paperRepository.findByPaperId(paperId).map(paper -> {

            // Increment view count (fire and forget — non-critical)
            try {
                paper.setViewCount((paper.getViewCount() == null ? 0L : paper.getViewCount()) + 1);
                paperRepository.save(paper);
            } catch (Exception e) {
                log.warn("Failed to increment view count for {}: {}", paperId, e.getMessage());
            }

            // Fetch questions in the order listed in the paper
            List<Question> questions = questionRepository.findByPaperId(paperId);
            // Preserve paper's questionIds order
            List<String> order = paper.getQuestionIds();
            questions.sort(Comparator.comparingInt(q ->
                    order == null ? 0 : order.indexOf(q.getQuestionId())));

            List<QuestionDto> questionDtos = questions.stream()
                    .map(QuestionDto::from)
                    .toList();

            return PaperDetailDto.from(paper, questionDtos);
        });
    }

    // ── Filter dropdown options ──────────────────────────────────────
    public FilterOptionsDto getFilterOptions() {
        List<Paper> all = paperRepository.findByIsPublicTrueAndApprovedAtIsNotNull();

        List<String> boards = all.stream()
                .map(Paper::getBoard).filter(b -> b != null)
                .distinct().sorted().toList();

        List<String> classes = all.stream()
                .map(Paper::getClassLevel).filter(c -> c != null)
                .distinct().sorted().toList();

        List<String> subjects = all.stream()
                .map(Paper::getSubject).filter(s -> s != null)
                .distinct().sorted().toList();

        List<Integer> years = all.stream()
                .map(Paper::getYear).filter(y -> y != null)
                .distinct().sorted(Comparator.reverseOrder()).toList();

        return new FilterOptionsDto(boards, classes, subjects, years);
    }

    // ── Record a download (increment counter) ────────────────────────
    public Optional<Paper> recordDownloadAndGet(String paperId) {
        return paperRepository.findByPaperId(paperId).map(paper -> {
            try {
                paper.setDownloadCount(
                        (paper.getDownloadCount() == null ? 0L : paper.getDownloadCount()) + 1);
                paperRepository.save(paper);
            } catch (Exception e) {
                log.warn("Failed to increment download count for {}: {}", paperId, e.getMessage());
            }
            return paper;
        });
    }

    // ── Helper ───────────────────────────────────────────────────────
    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}