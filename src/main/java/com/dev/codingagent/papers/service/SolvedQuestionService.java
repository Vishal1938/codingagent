package com.dev.codingagent.papers.service;




import com.dev.codingagent.papers.entity.Question;
import com.dev.codingagent.papers.repository.QuestionRepository;
import com.dev.codingagent.solver.dto.ExtractedQuestion;
import com.dev.codingagent.solver.dto.QuestionAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists the structured Q&A produced by a solve job, so the UI can render
 * questions + answers inline (instead of only offering a PDF download).
 *
 * Reuses the same Question entity as Past Papers, linked via jobId.
 * This means a solved question can later be shared to the community by simply
 * setting its paperId + isPublic — no re-extraction or regeneration needed.
 */
@Service
public class SolvedQuestionService {

    private static final Logger log = LoggerFactory.getLogger(SolvedQuestionService.class);

    private final QuestionRepository questionRepository;

    public SolvedQuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /**
     * Save the Q&A from a completed solve job.
     *
     * @param jobId      the solve job id
     * @param userEmail  owner
     * @param questions  extracted questions (index-aligned with answers)
     * @param answers    generated answers (index-aligned with questions)
     * @return the saved Question documents
     */
    public List<Question> persistSolvedQA(String jobId,
                                          String userEmail,
                                          List<ExtractedQuestion> questions,
                                          List<QuestionAnswer> answers) {
        if (questions == null || questions.isEmpty()) {
            log.warn("📝  [SolvedQA] no questions to persist for job {}", jobId);
            return List.of();
        }

        List<Question> docs = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            ExtractedQuestion eq = questions.get(i);
            String qId = "q_" + UUID.randomUUID().toString().replace("-", "");

            Question q = new Question(qId, eq.question(), eq.type(), null);  // paperId null — not a paper yet
            q.setJobId(jobId);
            q.setExpectedAnswer(i < answers.size() ? answers.get(i).answer() : "");
            q.setSource("solved");
            q.setIsPublic(false);          // private to the user until shared
            q.setCreatedBy(userEmail);
            docs.add(q);
        }

        questionRepository.saveAll(docs);
        log.info("📝  [SolvedQA] persisted {} questions for job {}", docs.size(), jobId);
        return docs;
    }
}