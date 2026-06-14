package com.dev.codingagent.quiz.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A single MCQ within a quiz.
 */
@Document(collection = "quiz_questions")
public class QuizQuestion {

    @Id
    private String id;

    private String quizQuestionId;

    @Indexed
    private String quizId;

    private Integer questionNumber;       // order in the quiz
    private String  text;
    private List<String> options;
    private Integer correctOption;        // 0-based index
    private String  explanation;          // why correct (LLM)
    private String  topic;                // optional tag
    private Boolean answerConfident = true;  // false if LLM was unsure

    public QuizQuestion() {}

    // ── Getters & setters ────────────────────────────────────────
    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getQuizQuestionId()           { return quizQuestionId; }
    public void   setQuizQuestionId(String s)   { this.quizQuestionId = s; }
    public String getQuizId()                   { return quizId; }
    public void   setQuizId(String s)           { this.quizId = s; }
    public Integer getQuestionNumber()          { return questionNumber; }
    public void    setQuestionNumber(Integer n) { this.questionNumber = n; }
    public String getText()                     { return text; }
    public void   setText(String s)             { this.text = s; }
    public List<String> getOptions()            { return options; }
    public void         setOptions(List<String> o) { this.options = o; }
    public Integer getCorrectOption()           { return correctOption; }
    public void    setCorrectOption(Integer i)  { this.correctOption = i; }
    public String getExplanation()              { return explanation; }
    public void   setExplanation(String s)      { this.explanation = s; }
    public String getTopic()                    { return topic; }
    public void   setTopic(String s)            { this.topic = s; }
    public Boolean getAnswerConfident()         { return answerConfident; }
    public void    setAnswerConfident(Boolean b){ this.answerConfident = b; }
}