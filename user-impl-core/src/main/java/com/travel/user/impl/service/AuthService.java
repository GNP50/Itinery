package com.travel.user.impl.service;

import com.travel.user.api.dto.AuthDTO;
import com.travel.user.api.port.inbound.AuthUseCase;
import com.travel.user.api.port.outbound.UserPersistencePort;
import com.travel.user.impl.exception.AuthException;
import com.travel.user.impl.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Core authentication service.
 * <p>
 * Handles user registration, login, anonymous session creation and current-user
 * extraction from the Spring Security context.
 * Implements the AuthUseCase inbound port.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

    private final UserPersistencePort<User> userRepo;
    private final JwtTokenService jwtTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthDTO.Response register(String email, String password, String name) {
        if (userRepo.findByEmail(email).isPresent()) {
            throw new AuthException("Email already registered: " + email);
        }
        User user = User.createRegistered(email, name, passwordEncoder.encode(password));
        user = userRepo.save(user);
        log.info("Registered new user id={} email={}", user.getId(), email);
        return buildResponse(user);
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthDTO.Response login(String email, String password) {
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new AuthException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }
        log.info("User logged in id={}", user.getId());
        return buildResponse(user);
    }

    // -------------------------------------------------------------------------
    // Anonymous
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthDTO.Response createAnonymous() {
        User user = User.createAnonymous();
        user = userRepo.save(user);
        log.info("Created anonymous user id={} sub={}", user.getId(), user.getSub());
        return buildResponse(user);
    }

    // -------------------------------------------------------------------------
    // Current-user extraction
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String userIdStr = jwtAuth.getToken().getClaimAsString("userId");
            if (userIdStr != null) {
                try {
                    return Optional.of(UUID.fromString(userIdStr));
                } catch (IllegalArgumentException ex) {
                    log.warn("JWT contains invalid userId claim: {}", userIdStr);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public AuthDTO.StatusResponse getStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String userIdStr  = jwtAuth.getToken().getClaimAsString("userId");
            String userType   = jwtAuth.getToken().getClaimAsString("userType");
            if (userIdStr != null) {
                try {
                    return new AuthDTO.StatusResponse(UUID.fromString(userIdStr), userType, true);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return new AuthDTO.StatusResponse(null, null, false);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AuthDTO.Response buildResponse(User user) {
        String token = jwtTokenService.generateToken(user);
        return new AuthDTO.Response(user.getId(), token, user.getUserType().name());
    }
}
