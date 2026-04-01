package com.travel.user.impl.service;

import com.travel.user.impl.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues and encodes JWT bearer tokens for authenticated users.
 * <p>
 * Tokens are signed with HMAC-SHA256 using a symmetric key configured in
 * {@code application.yml} under {@code jwt.secret} (base64-encoded 256-bit value).
 * If the property is absent a random key is generated at startup (local dev only).
 *
 * <h3>Claims layout</h3>
 * <pre>
 * {
 *   "iss":      "itinerary-service",
 *   "sub":      "&lt;user.sub&gt;",
 *   "userId":   "&lt;user.id UUID&gt;",
 *   "userType": "REGISTERED | ANONYMOUS",
 *   "iat":      &lt;issued-at epoch seconds&gt;,
 *   "exp":      &lt;expiry epoch seconds&gt;
 * }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    private final JwtEncoder jwtEncoder;

    /**
     * Generates a signed JWT for the given user.
     *
     * @param user the user entity; must not be {@code null}
     * @return compact JWT string suitable for the {@code Authorization: Bearer} header
     */
    public String generateToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("itinerary-service")
            .issuedAt(now)
            .expiresAt(now.plus(TOKEN_TTL))
            .subject(user.getSub())
            .claim("userId",   user.getId().toString())
            .claim("userType", user.getUserType().name())
            .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        log.debug("Issued JWT for user id={} type={}", user.getId(), user.getUserType());
        return token;
    }
}
