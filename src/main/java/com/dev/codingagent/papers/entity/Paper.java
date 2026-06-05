package com.dev.codingagent.papers.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Paper — a collection of questions forming a single test/exam.
 * Container that references questions by id.
 */
@Document(collection = "papers")
@CompoundIndexes({
        @CompoundIndex(name = "filter_idx",
                def = "{'board': 1, 'class': 1, 'subject': 1, 'year': 1}"),
        @CompoundIndex(name = "public_approved_idx",
                def = "{'isPublic': 1, 'approvedAt': 1}")
})
public class Paper {

    @Id
    private String id;

    @Indexed(unique = true)
    private String paperId;

    private String title;             // "CBSE Class 10 Mathematics 2024"

    // Filters
    private String board;
    @org.springframework.data.mongodb.core.mapping.Field("class")
    private String classLevel;
    private String subject;
    private Integer year;

    // Contents
    private List<String> questionIds = new ArrayList<>();
    private Integer totalMarks;
    private Integer durationMinutes;

    // Provenance
    private String source;            // official | extracted | user-uploaded
    private String originalPdfUrl;    // Cloudinary URL of original paper
    private String answerPdfUrl;      // Cloudinary URL of generated answer PDF
    private String uploadedBy;        // admin email or userEmail

    // Visibility & moderation
    private Boolean isPublic = true;
    private LocalDateTime approvedAt;
    private String approvedBy;

    // Denormalized stats
    private Integer questionCount = 0;
    private Long viewCount        = 0L;
    private Long downloadCount    = 0L;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Constructors ─────────────────────────────────────────────
    public Paper() {}

    // ── Getters & setters ────────────────────────────────────────
    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }
    public String getPaperId()                     { return paperId; }
    public void   setPaperId(String s)             { this.paperId = s; }
    public String getTitle()                       { return title; }
    public void   setTitle(String s)               { this.title = s; }
    public String getBoard()                       { return board; }
    public void   setBoard(String s)               { this.board = s; }
    public String getClassLevel()                  { return classLevel; }
    public void   setClassLevel(String s)          { this.classLevel = s; }
    public String getSubject()                     { return subject; }
    public void   setSubject(String s)             { this.subject = s; }
    public Integer getYear()                       { return year; }
    public void    setYear(Integer y)              { this.year = y; }
    public List<String> getQuestionIds()                  { return questionIds; }
    public void         setQuestionIds(List<String> q)    { this.questionIds = q; }
    public Integer getTotalMarks()                 { return totalMarks; }
    public void    setTotalMarks(Integer m)        { this.totalMarks = m; }
    public Integer getDurationMinutes()            { return durationMinutes; }
    public void    setDurationMinutes(Integer d)   { this.durationMinutes = d; }
    public String getSource()                      { return source; }
    public void   setSource(String s)              { this.source = s; }
    public String getOriginalPdfUrl()              { return originalPdfUrl; }
    public void   setOriginalPdfUrl(String s)      { this.originalPdfUrl = s; }
    public String getAnswerPdfUrl()                { return answerPdfUrl; }
    public void   setAnswerPdfUrl(String s)        { this.answerPdfUrl = s; }
    public String getUploadedBy()                  { return uploadedBy; }
    public void   setUploadedBy(String s)          { this.uploadedBy = s; }
    public Boolean getIsPublic()                   { return isPublic; }
    public void    setIsPublic(Boolean b)          { this.isPublic = b; }
    public LocalDateTime getApprovedAt()           { return approvedAt; }
    public void          setApprovedAt(LocalDateTime t) { this.approvedAt = t; }
    public String getApprovedBy()                  { return approvedBy; }
    public void   setApprovedBy(String s)          { this.approvedBy = s; }
    public Integer getQuestionCount()              { return questionCount; }
    public void    setQuestionCount(Integer c)     { this.questionCount = c; }
    public Long getViewCount()                     { return viewCount; }
    public void setViewCount(Long c)               { this.viewCount = c; }
    public Long getDownloadCount()                 { return downloadCount; }
    public void setDownloadCount(Long c)           { this.downloadCount = c; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}