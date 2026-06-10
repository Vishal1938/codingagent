package com.dev.codingagent.papers.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Question — the atom of the platform.
 * A single question extracted from a paper, with educational metadata
 * (subject, topic, difficulty), provenance, and answer.
 *
 * Same question can be referenced by multiple papers (sample papers
 * often reuse questions from official ones).
 */
@Document(collection = "questions")
@CompoundIndexes({
        @CompoundIndex(name = "subject_topic_idx",
                def = "{'subject': 1, 'topic': 1}"),
        @CompoundIndex(name = "board_class_subject_year_idx",
                def = "{'board': 1, 'class': 1, 'subject': 1, 'year': 1}")
})
public class Question {

    @Id
    private String id;

    @Indexed(unique = true)
    private String questionId;        // public-facing UUID

    private String text;
    private String type;              // MCQ | short_answer | descriptive | numerical | true_false | fill_blank

    // Educational metadata (auto-tagged by LLM, admin-editable)
    private String subject;
    private String topic;
    private String subtopic;

    @Indexed
    private String jobId;          // ← NEW — set when question comes from a solve job

    public String getJobId()              { return jobId; }
    public void   setJobId(String jobId)  { this.jobId = jobId; }

    // Source identification
    private String board;             // CBSE | ICSE | State-UP | etc.
    @org.springframework.data.mongodb.core.mapping.Field("class")
    private String classLevel;        // 9 | 10 | 11 | 12 — using classLevel to avoid Java keyword clash
    private Integer year;

    @Indexed
    private String paperId;           // ref to papers collection

    // Details
    private String difficulty;        // easy | medium | hard
    private Integer marks;

    // Answer
    private String expectedAnswer;
    private List<String> options;     // for MCQ
    private Integer correctOption;    // index of correct option

    // Provenance
    private String source;            // extracted | curated | user-uploaded
    private Boolean isPublic = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String createdBy;         // userEmail or "system"

    // ── Constructors ─────────────────────────────────────────────
    public Question() {}

    public Question(String questionId, String text, String type, String paperId) {
        this.questionId = questionId;
        this.text       = text;
        this.type       = type;
        this.paperId    = paperId;
    }

    // ── Getters & setters ────────────────────────────────────────
    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }
    public String getQuestionId()                  { return questionId; }
    public void   setQuestionId(String s)          { this.questionId = s; }
    public String getText()                        { return text; }
    public void   setText(String s)                { this.text = s; }
    public String getType()                        { return type; }
    public void   setType(String s)                { this.type = s; }
    public String getSubject()                     { return subject; }
    public void   setSubject(String s)             { this.subject = s; }
    public String getTopic()                       { return topic; }
    public void   setTopic(String s)               { this.topic = s; }
    public String getSubtopic()                    { return subtopic; }
    public void   setSubtopic(String s)            { this.subtopic = s; }
    public String getBoard()                       { return board; }
    public void   setBoard(String s)               { this.board = s; }
    public String getClassLevel()                  { return classLevel; }
    public void   setClassLevel(String s)          { this.classLevel = s; }
    public Integer getYear()                       { return year; }
    public void    setYear(Integer y)              { this.year = y; }
    public String getPaperId()                     { return paperId; }
    public void   setPaperId(String s)             { this.paperId = s; }
    public String getDifficulty()                  { return difficulty; }
    public void   setDifficulty(String s)          { this.difficulty = s; }
    public Integer getMarks()                      { return marks; }
    public void    setMarks(Integer m)             { this.marks = m; }
    public String  getExpectedAnswer()             { return expectedAnswer; }
    public void    setExpectedAnswer(String s)     { this.expectedAnswer = s; }
    public List<String> getOptions()               { return options; }
    public void         setOptions(List<String> o) { this.options = o; }
    public Integer getCorrectOption()              { return correctOption; }
    public void    setCorrectOption(Integer i)     { this.correctOption = i; }
    public String getSource()                      { return source; }
    public void   setSource(String s)              { this.source = s; }
    public Boolean getIsPublic()                   { return isPublic; }
    public void    setIsPublic(Boolean b)          { this.isPublic = b; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public String getCreatedBy()                   { return createdBy; }
    public void   setCreatedBy(String s)           { this.createdBy = s; }
}