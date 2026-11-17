package com.tsukinotes.api.controller;

import com.tsukinotes.api.auth.AuthInterceptor;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.service.IdentityService;
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

    public AuthController(IdentityService identityService, SessionService sessionService) {
        this.identityService = identityService;
        this.sessionService = sessionService;
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
