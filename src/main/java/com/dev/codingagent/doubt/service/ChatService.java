package com.dev.codingagent.doubt.service;

import com.dev.codingagent.doubt.entity.ChatMessage;
import com.dev.codingagent.doubt.entity.ChatSession;
import com.dev.codingagent.doubt.entity.DocumentChunk;
import com.dev.codingagent.doubt.entity.DocumentEntity;
import com.dev.codingagent.doubt.dto.ChatResponse;
import com.dev.codingagent.doubt.repository.ChatMessageRepository;
import com.dev.codingagent.doubt.repository.ChatSessionRepository;
import com.dev.codingagent.doubt.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The RAG chat engine.
 *
 * On each user message:
 *   1. embed the question
 *   2. retrieve top-K most relevant chunks from the document (vector search)
 *   3. build a grounded prompt (retrieved context + recent conversation history)
 *   4. ask the LLM to answer using ONLY the document context
 *   5. persist both the user message and the assistant reply
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final int TOP_K = 5;             // chunks to retrieve
    private static final int HISTORY_TURNS = 6;     // recent messages for memory
    private static final double MIN_SIMILARITY = 0.15;  // floor to consider a chunk relevant

    private static final String SYSTEM_PROMPT = """
            You are a study assistant that answers questions using ONLY the provided
            document context. The student has uploaded their own notes/textbook and
            wants to learn from THAT material specifically.

            Rules:
            - Answer based on the CONTEXT below. Do not bring in outside facts that
              contradict it.
            - If the context doesn't contain enough to answer, say so honestly and
              suggest what the student might look for — don't invent content.
            - Be clear and educational, like a good tutor. Use examples from the context.
            - For math/science, use LaTeX notation (\\( \\) for inline, \\[ \\] for display).
            - If asked to generate practice questions (MCQs etc.), base them strictly
              on the context provided.
            """;

    private final ChatClient            chatClient;
    private final EmbeddingService      embeddingService;
    private final VectorSearchService   vectorSearch;
    private final DocumentRepository    documentRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public ChatService(ChatClient.Builder builder,
                       EmbeddingService embeddingService,
                       VectorSearchService vectorSearch,
                       DocumentRepository documentRepository,
                       ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository) {
        this.chatClient         = builder.build();
        this.embeddingService   = embeddingService;
        this.vectorSearch       = vectorSearch;
        this.documentRepository = documentRepository;
        this.sessionRepository  = sessionRepository;
        this.messageRepository  = messageRepository;
    }

    /** Create a new chat session for a document. */
    public ChatSession createSession(String documentId, String userEmail) {
        DocumentEntity doc = documentRepository
                .findByDocumentIdAndUserId(documentId, userEmail)
                .orElseThrow(() -> new IllegalStateException("Document not found or not yours"));
        if (!"READY".equals(doc.getStatus())) {
            throw new IllegalStateException("Document is still processing");
        }

        ChatSession session = new ChatSession();
        session.setSessionId("sess_" + UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userEmail);
        session.setDocumentId(documentId);
        session.setTitle("New chat");
        sessionRepository.save(session);
        log.info("💬  Created session {} for doc {}", session.getSessionId(), documentId);
        return session;
    }

    /** Process a user message → RAG answer. */
    public ChatResponse sendMessage(String sessionId, String userEmail, String userMessage) {
        ChatSession session = sessionRepository
                .findBySessionIdAndUserId(sessionId, userEmail)
                .orElseThrow(() -> new IllegalStateException("Session not found or not yours"));

        log.info("💬  [Session {}] message: {}", sessionId,
                userMessage.length() > 60 ? userMessage.substring(0, 60) + "…" : userMessage);

        // 1. Embed the question
        List<Double> queryEmbedding = embeddingService.embed(userMessage);

        // 2. Retrieve top-K relevant chunks
        List<DocumentChunk> chunks = vectorSearch.topK(
                session.getDocumentId(), queryEmbedding, TOP_K);

        // Filter by a minimum similarity so we don't stuff irrelevant context
        List<DocumentChunk> relevant = new ArrayList<>();
        for (DocumentChunk c : chunks) {
            double sim = VectorSearchService.cosineSimilarity(queryEmbedding, c.getEmbedding());
            if (sim >= MIN_SIMILARITY) relevant.add(c);
        }
        boolean grounded = !relevant.isEmpty();

        // 3. Build context block
        StringBuilder context = new StringBuilder();
        List<String> citedIds = new ArrayList<>();
        for (int i = 0; i < relevant.size(); i++) {
            DocumentChunk c = relevant.get(i);
            context.append("[Passage ").append(i + 1).append("]\n")
                    .append(c.getText()).append("\n\n");
            citedIds.add(c.getChunkId());
        }

        // 4. Recent conversation history (memory)
        List<ChatMessage> history = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);
        StringBuilder historyBlock = new StringBuilder();
        int from = Math.max(0, history.size() - HISTORY_TURNS);
        for (int i = from; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            historyBlock.append(m.getRole().equals("user") ? "Student: " : "Assistant: ")
                    .append(m.getContent()).append("\n");
        }

        // 5. Compose the user prompt
        String userPrompt = """
                %s
                CONTEXT FROM THE DOCUMENT:
                %s

                QUESTION:
                %s
                """.formatted(
                historyBlock.length() > 0
                        ? "RECENT CONVERSATION:\n" + historyBlock + "\n" : "",
                grounded ? context.toString()
                        : "(No closely matching passages were found in the document.)",
                userMessage);

        // 6. Call the LLM
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        // 7. Persist both messages
        ChatMessage userMsg = new ChatMessage(
                "msg_" + UUID.randomUUID().toString().replace("-", ""),
                sessionId, "user", userMessage);
        messageRepository.save(userMsg);

        ChatMessage assistantMsg = new ChatMessage(
                "msg_" + UUID.randomUUID().toString().replace("-", ""),
                sessionId, "assistant", answer);
        assistantMsg.setCitedChunkIds(citedIds);
        messageRepository.save(assistantMsg);

        // 8. Update session (title from first question + bump lastMessageAt)
        if (history.isEmpty()) {
            String t = userMessage.length() > 50 ? userMessage.substring(0, 50) + "…" : userMessage;
            session.setTitle(t);
        }
        session.setLastMessageAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("💬  [Session {}] answered (grounded={}, {} chunks)", sessionId, grounded, citedIds.size());
        return new ChatResponse(sessionId, answer, grounded);
    }
}