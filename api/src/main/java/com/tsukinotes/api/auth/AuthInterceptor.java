package com.tsukinotes.api.auth;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires a valid "Authorization: Bearer <token>" header and exposes the logged-in identity
 * to controllers as a request attribute.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String IDENTITY_ATTRIBUTE = "authenticatedIdentity";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionService sessionService;

    public AuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Identity identity = sessionService.findIdentity(extractToken(request))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));

        request.setAttribute(IDENTITY_ATTRIBUTE, identity);
        return true;
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}
