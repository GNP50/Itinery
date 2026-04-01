package com.travel.user.impl.adapter.inbound.rest;

import com.travel.user.api.dto.AuthDTO;
import com.travel.user.impl.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST adapter for authentication endpoints.
 * <p>
 * All {@code /api/v1/auth/**} paths are public; callers must complete one of:
 * <ul>
 *   <li>{@code POST /api/v1/auth/register} – create a registered account</li>
 *   <li>{@code POST /api/v1/auth/login}    – authenticate an existing account</li>
 *   <li>{@code POST /api/v1/auth/anonymous} – start a guest session</li>
 * </ul>
 * Each returns a JWT bearer token to be included as
 * {@code Authorization: Bearer <token>} on subsequent requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, or start an anonymous session")
public class AuthRestController {

    private final AuthService authService;

    // -------------------------------------------------------------------------
    // POST /register
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Register a new user",
        description = "Creates a registered account and returns a JWT bearer token."
    )
    @PostMapping(
        value    = "/register",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AuthDTO.Response> register(
            @RequestBody AuthDTO.RegisterRequest request) {

        log.debug("POST /api/v1/auth/register email={}", request.email());
        return ResponseEntity.ok(authService.register(request.email(), request.password(), request.name()));
    }

    // -------------------------------------------------------------------------
    // POST /login
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Login with email and password",
        description = "Validates credentials and returns a JWT bearer token."
    )
    @PostMapping(
        value    = "/login",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AuthDTO.Response> login(
            @RequestBody AuthDTO.LoginRequest request) {

        log.debug("POST /api/v1/auth/login email={}", request.email());
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    // -------------------------------------------------------------------------
    // POST /anonymous
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Start an anonymous session",
        description = "Creates a guest user identity and returns a JWT bearer token. "
                    + "Each call generates a new anonymous user."
    )
    @PostMapping(value = "/anonymous", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthDTO.Response> anonymous() {
        log.debug("POST /api/v1/auth/anonymous");
        return ResponseEntity.ok(authService.createAnonymous());
    }

    // -------------------------------------------------------------------------
    // GET /check
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Check authentication status",
        description = "Returns the current user's id and type if a valid JWT is present."
    )
    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthDTO.StatusResponse> check() {
        return ResponseEntity.ok(authService.getStatus());
    }
}
