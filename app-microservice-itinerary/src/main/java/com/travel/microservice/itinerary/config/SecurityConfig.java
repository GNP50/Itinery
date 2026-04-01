package com.travel.microservice.itinerary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Spring Security configuration for the Itinerary microservice.
 * <p>
 * Configures JWT validation only (no token generation - that's done by the User service).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    /**
     * Configures the security filter chain with JWT authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints - read operations
                .requestMatchers("GET", "/api/v1/**").permitAll()
                // Public endpoints - actuator
                .requestMatchers("/actuator/**").permitAll()
                // Protected endpoints - write operations require authentication
                .requestMatchers("POST", "/api/v1/**").authenticated()
                .requestMatchers("PUT", "/api/v1/**").authenticated()
                .requestMatchers("PATCH", "/api/v1/**").authenticated()
                .requestMatchers("DELETE", "/api/v1/**").authenticated()
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").authenticated()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Creates JWT decoder for validating tokens.
     * Itinerary service only validates tokens, doesn't create them.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = deriveSecretKey(jwtSecret);
        return NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    /**
     * Derives a 256-bit secret key from the configured JWT secret.
     */
    private SecretKey deriveSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            // Generate random key for development (NOT for production!)
            byte[] keyBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(keyBytes);
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        }

        try {
            // Hash the secret to ensure it's 256 bits
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
