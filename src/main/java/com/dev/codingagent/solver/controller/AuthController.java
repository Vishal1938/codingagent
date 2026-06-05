package com.dev.codingagent.solver.controller;

import com.dev.codingagent.solver.dto.auth.AuthDtos.*;
import com.dev.codingagent.solver.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     *
     * Body: { "email": "user@example.com", "password": "secret123" }
     *
     * Returns a JWT on success — client should store it and send it
     * as "Authorization: Bearer <token>" on every subsequent request.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        log.info("📡  POST /api/auth/register — {}", request.email());
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse(e.getMessage()));
        }
    }

    /**
     * POST /api/auth/login
     *
     * Body: { "email": "user@example.com", "password": "secret123" }
     *
     * Returns a fresh JWT on success.
     * Returns 401 automatically if credentials are wrong
     * (Spring Security handles that before this code runs).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("📡  POST /api/auth/login — {}", request.email());
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(new MessageResponse("Invalid email or password"));
        }
    }
}