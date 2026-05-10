package com.dev.codingagent.config;


import com.dev.codingagent.repository.UserRepository;
import com.dev.codingagent.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controller methods
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter    jwtAuthFilter;

    @Autowired
    private UserRepository   userRepository;

//    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserRepository userRepository) {
//        this.jwtAuthFilter  = jwtAuthFilter;
//        this.userRepository = userRepository;
//    }

    // ── Route-level security rules ────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless JWT APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — Spring Security must NOT create HttpSessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Route access rules
                .authorizeHttpRequests(auth -> auth

                        // ── Public endpoints — no token needed ─────────────────
                        .requestMatchers(
                                "/api/auth/**"          // register + login
                        ).permitAll()

                        // ── Protected endpoints — valid JWT required ────────────
                        .requestMatchers("/api/extractor/**").authenticated()
                        .requestMatchers("/api/solver/**").authenticated()
                        .requestMatchers("/api/pdf/**").authenticated()

                        // ── Admin-only endpoints ────────────────────────────────
                        // .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ── Everything else requires authentication ─────────────
                        .anyRequest().authenticated()
                )

                // Wire our JWT filter BEFORE the default username/password filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // Use our custom auth provider (loads from DB, uses BCrypt)
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    // ── UserDetailsService — loads user by email from DB ─────────────────

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));
    }

    // ── AuthenticationProvider — ties UserDetailsService + PasswordEncoder ─

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ── AuthenticationManager — needed by AuthService to trigger login ────

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── BCrypt password encoder ───────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}