package com.dev.codingagent.solver.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles all JWT operations:
 *  - generateToken()  → creates a signed JWT for a logged-in user
 *  - extractEmail()   → reads the subject (email) from a token
 *  - isTokenValid()   → verifies signature + expiry, confirms it belongs to the right user
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // ── Token generation ──────────────────────────────────────────────────

    public String generateToken(UserDetails userDetails) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())      // email goes here
                .claim("roles", userDetails.getAuthorities()
                        .stream()
                        .map(a -> a.getAuthority())
                        .toList())                       // e.g. ["ROLE_USER"]
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())                  // HS256 by default
                .compact();
    }

    // ── Token reading ─────────────────────────────────────────────────────

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    // ── Token validation ──────────────────────────────────────────────────

    /**
     * Returns true if:
     *  1. The signature is valid (not tampered with)
     *  2. The token has not expired
     *  3. The subject matches the provided UserDetails
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("⚠️  Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        // throws JwtException if signature is invalid or token is malformed
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        // Derive a strong HMAC key from the configured secret string
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
