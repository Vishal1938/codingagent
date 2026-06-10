package com.dev.codingagent.papers.service;

import com.dev.codingagent.papers.dto.PendingPaperDto;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.repository.PaperRepository;
import com.dev.codingagent.papers.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Admin-side moderation of community-submitted papers.
 * Approve → sets approvedAt, paper becomes visible in browse.
 * Reject → deletes the paper + its questions.
 */
@Service
public class PaperModerationService {

    private static final Logger log = LoggerFactory.getLogger(PaperModerationService.class);

    private final PaperRepository    paperRepository;
    private final QuestionRepository questionRepository;

    public PaperModerationService(PaperRepository paperRepository,
                                  QuestionRepository questionRepository) {
        this.paperRepository    = paperRepository;
        this.questionRepository = questionRepository;
    }

    /** All papers awaiting review (isPublic=true, approvedAt=null). */
    public List<PendingPaperDto> getPendingPapers() {
        return paperRepository.findByApprovedAtIsNullAndIsPublicTrue()
                .stream()
                .map(PendingPaperDto::from)
                .toList();
    }

    /** Approve a pending paper — it becomes visible in the public archive. */
    public Optional<Paper> approve(String paperId, String adminEmail) {
        return paperRepository.findByPaperId(paperId).map(paper -> {
            paper.setApprovedAt(LocalDateTime.now());
            paper.setApprovedBy(adminEmail);
            paperRepository.save(paper);
            log.info("✅  [Moderation] Approved paper {} by {}", paperId, adminEmail);
            return paper;
        });
    }

    /** Reject a pending paper — deletes it and its questions. */
    public boolean reject(String paperId, String adminEmail) {
        return paperRepository.findByPaperId(paperId).map(paper -> {
            // Only reject if still pending — don't delete already-approved papers
            if (paper.getApprovedAt() != null) {
                log.warn("⚠️  Cannot reject already-approved paper {}", paperId);
                return false;
            }
            questionRepository.findByPaperId(paperId).forEach(questionRepository::delete);
            paperRepository.delete(paper);
            log.info("🗑️  [Moderation] Rejected + deleted paper {} by {}", paperId, adminEmail);
            return true;
        }).orElse(false);
    }
}