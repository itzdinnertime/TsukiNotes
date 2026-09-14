package com.tsukinotes.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InviteGateFilter extends HttpFilter {

    @Value("${app.invite-token}")
    private String inviteToken;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String providedToken = request.getHeader("X-Invite-Token");

        if (providedToken == null || !providedToken.equals(inviteToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid invite token");
            return;
        }

        chain.doFilter(request, response);
    }
}
