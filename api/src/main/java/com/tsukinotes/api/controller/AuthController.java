package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.service.IdentityService;
import com.tsukinotes.api.service.PasswordResetService;
import com.tsukinotes.api.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IdentityService identityService;
    private final SessionService sessionService;
    private final PasswordResetService passwordResetService;

    public AuthController(IdentityService identityService, SessionService sessionService,
                          PasswordResetService passwordResetService) {
        this.identityService = identityService;
        this.sessionService = sessionService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/signup")
    public AuthResponse signUp(@RequestBody SignUpRequest request) {
        Identity identity = identityService.signUp(request.name(), request.email(), request.password());
        return new AuthResponse(sessionService.createSession(identity), identity);
    }

    @PostMapping("/login")
    public AuthResponse logIn(@RequestBody LogInRequest request, HttpServletRequest httpRequest) {
        // The direct connection's address. If this is ever put behind a reverse proxy, use the
        // proxy's trusted forwarded-for header instead, or every user will share the proxy's IP.
        Identity identity = identityService.logIn(request.email(), request.password(), httpRequest.getRemoteAddr());
        return new AuthResponse(sessionService.createSession(identity), identity);
    }

    /** Emails a reset link if the account exists. Always answers 204, so it doesn't reveal which emails exist. */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /** Sets a new password using the token from the emailed link. */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@RequestBody PasswordResetConfirm request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    public record PasswordResetRequest(String email) {
    }

    public record PasswordResetConfirm(String token, String password) {
    }

    @GetMapping("/me")
    public Identity me(@RequestAttribute(AuthInterceptor.IDENTITY_ATTRIBUTE) Identity identity) {
        return identity;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logOut(HttpServletRequest request) {
        sessionService.endSession(AuthInterceptor.extractToken(request));
        return ResponseEntity.noContent().build();
    }

    public record SignUpRequest(String name, String email, String password) {
    }

    public record LogInRequest(String email, String password) {
    }

    public record AuthResponse(String token, Identity identity) {
    }
}
