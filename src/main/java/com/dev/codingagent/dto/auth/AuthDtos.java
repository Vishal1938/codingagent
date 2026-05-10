
package com.dev.codingagent.dto.auth;
/**
 * All DTOs used by the auth endpoints, kept in one file for simplicity.
 */
public class AuthDtos {

    // ── POST /api/auth/register ────────────────────────────────────────────

    public record RegisterRequest(
            String email,
            String password
    ) {}

    // ── POST /api/auth/login ───────────────────────────────────────────────

    public record LoginRequest(
            String email,
            String password
    ) {}

    // ── Response returned after successful register or login ───────────────

    public record AuthResponse(
            String token,           // the JWT
            String email,
            String role,
            long   expiresInMs      // how long until the token expires
    ) {}

    // ── Generic message response (e.g. for errors) ─────────────────────────

    public record MessageResponse(String message) {}
}