package com.dev.codingagent.service;


import com.dev.codingagent.dto.auth.AuthDtos.*;
import com.dev.codingagent.entity.User;
import com.dev.codingagent.repository.UserRepository;
import com.dev.codingagent.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authManager) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
        this.authManager     = authManager;
    }

    // ── Register ──────────────────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        log.info("📝  Registering new user: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password())); // BCrypt hash
        user.setRole(User.Role.USER);

        userRepository.save(user);
        log.info("✅  User registered: {}", user.getEmail());

        String token = jwtService.generateToken(user);
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationMs()
        );
    }

    // ── Login ─────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        log.info("🔐  Login attempt: {}", request.email());

        // AuthenticationManager handles credential validation.
        // Throws AuthenticationException if credentials are wrong —
        // Spring Security automatically maps this to 401.
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        // Credentials are valid — load user and issue token
        User user = userRepository.findByEmail(request.email())
                .orElseThrow();   // can't be empty here — authenticate() passed

        String token = jwtService.generateToken(user);
        log.info("✅  Login successful: {}", user.getEmail());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getRole().name(),
                jwtService.getExpirationMs()
        );
    }
}