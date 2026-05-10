package com.dev.codingagent.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request (OncePerRequestFilter).
 *
 * Flow:
 *  1. Read the "Authorization: Bearer <token>" header
 *  2. Extract the email from the JWT
 *  3. Load the user from the DB
 *  4. Validate the token
 *  5. Set the authentication in SecurityContext so Spring Security
 *     knows the request is authenticated for the rest of the chain
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService        jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, @Lazy UserDetailsService userDetailsService) {
        this.jwtService         = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Pass through if no Bearer token present (public endpoints, login, register)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt   = authHeader.substring(7);   // strip "Bearer "
        final String email;

        try {
            email = jwtService.extractEmail(jwt);
        } catch (Exception e) {
            log.warn("⚠️  Could not extract email from JWT: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Only authenticate if not already authenticated in this request
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                // Build Spring Security authentication object and set it in context
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                          // credentials null — already authenticated
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("✅  JWT authenticated: {}", email);
            } else {
                log.warn("⚠️  JWT validation failed for: {}", email);
            }
        }

        filterChain.doFilter(request, response);
    }
}