package com.dev.codingagent.papers.dto;


import com.dev.codingagent.papers.entity.Question;
import java.util.List;

/**
 * Public-facing question shape for the paper detail view.
 * Includes the answer since these are study materials.
 */
public record QuestionDto(
        String       questionId,
        String       text,
        String       type,
        String       topic,
        String       difficulty,
        Integer      marks,
        String       expectedAnswer,
        List<String> options,
        Integer      correctOption
) {
    public static QuestionDto from(Question q) {
        return new QuestionDto(
                q.getQuestionId(),
                q.getText(),
                q.getType(),
                q.getTopic(),
                q.getDifficulty(),
                q.getMarks(),
                q.getExpectedAnswer(),
                q.getOptions(),
                q.getCorrectOption()
        );
    }
}