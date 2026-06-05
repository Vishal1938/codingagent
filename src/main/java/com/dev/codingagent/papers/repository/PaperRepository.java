package com.dev.codingagent.papers.repository;

import com.dev.codingagent.papers.entity.Paper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaperRepository extends MongoRepository<Paper, String> {

    Optional<Paper> findByPaperId(String paperId);

    // Pending review (PR-3 — community submissions awaiting approval)
    List<Paper> findByApprovedAtIsNullAndIsPublicTrue();

    // ── Browse search with optional filters ──────────────────────────
    // Each filter is applied only if non-null (SpEL conditional).
    // Only approved + public papers are returned.
    @Query("""
            { 'isPublic': true, 'approvedAt': { $ne: null },
              $and: [
                ?#{ [0] == null ? { $expr: true } : { 'board':   [0] } },
                ?#{ [1] == null ? { $expr: true } : { 'class':   [1] } },
                ?#{ [2] == null ? { $expr: true } : { 'subject': [2] } },
                ?#{ [3] == null ? { $expr: true } : { 'year':    [3] } }
              ]
            }
            """)
    Page<Paper> search(String board, String classLevel, String subject,
                       Integer year, Pageable pageable);

    // ── Text search across title (for the search bar) ────────────────
    @Query("""
            { 'isPublic': true, 'approvedAt': { $ne: null },
              'title': { $regex: ?0, $options: 'i' } }
            """)
    Page<Paper> searchByTitle(String titleRegex, Pageable pageable);

    // ── For computing filter dropdown options ────────────────────────
    List<Paper> findByIsPublicTrueAndApprovedAtIsNotNull();
}