package com.dev.codingagent.doubt.controller;

import com.dev.codingagent.doubt.dto.*;
import com.dev.codingagent.doubt.entity.ChatSession;
import com.dev.codingagent.doubt.repository.ChatMessageRepository;
import com.dev.codingagent.doubt.repository.ChatSessionRepository;
import com.dev.codingagent.doubt.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chat endpoints for Doubt Chat (PR-2):
 * create session, list sessions for a doc, send a message (RAG), fetch history.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService            chatService;
    private final ChatSessionRepository  sessionRepository;
    private final ChatMessageRepository  messageRepository;

    public ChatController(ChatService chatService,
                          ChatSessionRepository sessionRepository,
                          ChatMessageRepository messageRepository) {
        this.chatService       = chatService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    /** Start a new chat session for a document. */
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody CreateSessionRequest request,
                                           @AuthenticationPrincipal UserDetails user) {
        log.info("📡  POST /api/chat/sessions | doc: {} | user: {}",
                request.documentId(), user.getUsername());
        try {
            ChatSession session = chatService.createSession(request.documentId(), user.getUsername());
            return ResponseEntity.ok(ChatSessionDto.from(session));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** List chat sessions for a document. */
    @GetMapping("/sessions/{documentId}")
    public ResponseEntity<List<ChatSessionDto>> sessionsForDoc(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails user) {
        List<ChatSessionDto> sessions = sessionRepository
                .findByDocumentIdAndUserIdOrderByLastMessageAtDesc(documentId, user.getUsername())
                .stream().map(ChatSessionDto::from).toList();
        return ResponseEntity.ok(sessions);
    }

    /** Send a message → RAG answer. */
    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageRequest request,
                                         @AuthenticationPrincipal UserDetails user) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is empty"));
        }
        try {
            ChatResponse response = chatService.sendMessage(
                    request.sessionId(), user.getUsername(), request.message());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌  Chat message failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Chat failed: " + e.getMessage()));
        }
    }

    /** Fetch full conversation history for a session. */
    @GetMapping("/messages/{sessionId}")
    public ResponseEntity<?> messages(@PathVariable String sessionId,
                                      @AuthenticationPrincipal UserDetails user) {
        // Ownership check via session
        boolean owns = sessionRepository
                .findBySessionIdAndUserId(sessionId, user.getUsername())
                .isPresent();
        if (!owns) {
            return ResponseEntity.status(403).body(Map.of("error", "Not your session"));
        }
        List<ChatMessageDto> msgs = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream().map(ChatMessageDto::from).toList();
        return ResponseEntity.ok(msgs);
    }
}