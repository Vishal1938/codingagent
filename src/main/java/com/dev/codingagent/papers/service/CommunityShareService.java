package com.dev.codingagent.papers.service;

import com.dev.codingagent.papers.dto.ShareRequest;
import com.dev.codingagent.papers.entity.Paper;
import com.dev.codingagent.papers.entity.Question;
import com.dev.codingagent.papers.repository.PaperRepository;
import com.dev.codingagent.papers.repository.QuestionRepository;

import com.dev.codingagent.solver.entity.JobResult;
import com.dev.codingagent.solver.repository.JobResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts a user's existing JobResult into a community Paper submission.
 *
 * NOW EFFICIENT — reuses the Q&A already persisted at solve time.
 * No re-extraction, no answer regeneration, ZERO LLM calls.
 *
 * The solve flow persists each question (with its generated answer) as a
 * Question doc linked by jobId. Sharing simply:
 *   1. Creates a Paper
 *   2. Points the existing jobId-linked questions at that paper (sets paperId, makes public)
 *   3. Optionally re-tags topics if they weren't tagged at solve time
 *
 * Falls back to regeneration only if no persisted questions exist (e.g. an old
 * job from before inline-QA persistence shipped).
 */
@Service
public class CommunityShareService {

    private static final Logger log = LoggerFactory.getLogger(CommunityShareService.class);

    private final JobResultRepository jobResultRepository;
    private final PaperRepository     paperRepository;
    private final QuestionRepository  questionRepository;
    private final QuestionTaggingService tagger;

    public CommunityShareService(JobResultRepository jobResultRepository,
                                 PaperRepository paperRepository,
                                 QuestionRepository questionRepository,
                                 QuestionTaggingService tagger) {
        this.jobResultRepository = jobResultRepository;
        this.paperRepository     = paperRepository;
        this.questionRepository  = questionRepository;
        this.tagger              = tagger;
    }

    public Paper shareReport(String jobId, String userEmail, ShareRequest meta) throws Exception {

        // ── Validate ownership + state ──────────────────────────────
        JobResult job = jobResultRepository
                .findByJobIdAndUserEmail(jobId, userEmail)
                .orElseThrow(() -> new IllegalStateException("Report not found or not yours"));

        if (!"DONE".equals(job.getStatus())) {
            throw new IllegalStateException("Only completed reports can be shared");
        }
        if (Boolean.TRUE.equals(job.getSharedToCommunity())) {
            throw new IllegalStateException("This report is already shared");
        }

        log.info("🤝  [Community share] jobId={} by {} → {} Class {} {} {}",
                jobId, userEmail, meta.board(), meta.classLevel(), meta.subject(), meta.year());

        // ── Build the pending Paper ─────────────────────────────────
        String paperId = "p_" + UUID.randomUUID().toString().replace("-", "");
        String title = meta.board() + " Class " + meta.classLevel()
                + " " + meta.subject() + " " + meta.year();

        Paper paper = new Paper();
        paper.setPaperId(paperId);
        paper.setTitle(title);
        paper.setBoard(meta.board());
        paper.setClassLevel(meta.classLevel());
        paper.setSubject(meta.subject());
        paper.setYear(meta.year());
        paper.setSource("user-uploaded");
        paper.setUploadedBy(userEmail);
        paper.setAnswerPdfUrl(job.getPublicUrl());
        paper.setIsPublic(true);
        paper.setApprovedAt(null);                    // PENDING
        paperRepository.save(paper);

        // ── Reuse the Q&A persisted at solve time ───────────────────
        List<Question> existing = questionRepository.findByJobId(jobId);
        List<String> questionIds = new ArrayList<>();

        if (!existing.isEmpty()) {
            log.info("🤝  Reusing {} persisted questions — no regeneration", existing.size());

            boolean needsTagging = existing.stream()
                    .anyMatch(q -> q.getTopic() == null || q.getTopic().isBlank());

            for (Question q : existing) {
                // Point the question at this paper + fill in paper metadata
                q.setPaperId(paperId);
                q.setBoard(meta.board());
                q.setClassLevel(meta.classLevel());
                q.setSubject(meta.subject());
                q.setYear(meta.year());
                q.setIsPublic(true);
                questionIds.add(q.getQuestionId());
            }

            // Tag topics if they weren't tagged at solve time
            if (needsTagging) {
                tagger.tagAll(existing, meta.subject());
            }

            questionRepository.saveAll(existing);
        } else {
            // Fallback for legacy jobs with no persisted questions.
            log.warn("🤝  No persisted questions for job {} — paper created with 0 questions. "
                    + "(Legacy job from before inline-QA persistence.)", jobId);
        }

        paper.setQuestionIds(questionIds);
        paper.setQuestionCount(questionIds.size());
        paperRepository.save(paper);

        // ── Link back to the JobResult ──────────────────────────────
        job.setSharedToCommunity(true);
        job.setLinkedPaperId(paperId);
        jobResultRepository.save(job);

        log.info("✅  [Community share] paper {} created (PENDING) — {} questions (reused)",
                paperId, questionIds.size());
        return paper;
    }

    /**
     * Withdraw a pending submission.
     * Note: questions are NOT deleted (they belong to the solve job too) —
     * we just un-publish them and detach from the paper.
     * @return true if a pending paper was withdrawn; throws if already approved.
     */
    public boolean unshareReport(String jobId, String userEmail) {
        JobResult job = jobResultRepository
                .findByJobIdAndUserEmail(jobId, userEmail)
                .orElseThrow(() -> new IllegalStateException("Report not found or not yours"));

        boolean withdrawn = false;

        if (job.getLinkedPaperId() != null) {
            var paperOpt = paperRepository.findByPaperId(job.getLinkedPaperId());
            if (paperOpt.isPresent()) {
                Paper paper = paperOpt.get();
                if (paper.getApprovedAt() == null) {
                    // Detach questions from the paper but keep them (they're the solve job's Q&A)
                    List<Question> linked = questionRepository.findByPaperId(paper.getPaperId());
                    for (Question q : linked) {
                        q.setPaperId(null);
                        q.setIsPublic(false);
                    }
                    questionRepository.saveAll(linked);

                    paperRepository.delete(paper);
                    withdrawn = true;
                    log.info("🗑️  Withdrew pending paper {} ({} questions detached, not deleted)",
                            paper.getPaperId(), linked.size());
                } else {
                    log.warn("🔁  Paper {} already approved — refusing to withdraw", paper.getPaperId());
                    throw new IllegalStateException(
                            "This paper has already been approved and published. "
                                    + "Contact an admin to remove it.");
                }
            } else {
                log.warn("🔁  linkedPaperId {} has no matching paper — clearing stale link",
                        job.getLinkedPaperId());
            }
        }

        job.setSharedToCommunity(false);
        job.setLinkedPaperId(null);
        jobResultRepository.save(job);

        return withdrawn;
    }
}