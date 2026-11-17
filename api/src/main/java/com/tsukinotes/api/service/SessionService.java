package com.tsukinotes.api.service;

import com.tsukinotes.api.auth.SecureTokens;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Session;
import com.tsukinotes.api.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SessionService {

    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** Starts a session and returns the raw token. Only its hash is stored. */
    public String createSession(Identity identity) {
        String token = SecureTokens.generate();

        LocalDateTime now = LocalDateTime.now();
        Session session = new Session();
        session.setIdentity(identity);
        session.setTokenHash(SecureTokens.hash(token));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(SESSION_LIFETIME));
        sessionRepository.save(session);

        return token;
    }

    /** Returns the identity that owns this token, if the session exists and hasn't expired. */
    @Transactional
    public Optional<Identity> findIdentity(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Optional<Session> session = sessionRepository.findByTokenHash(SecureTokens.hash(token));
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (session.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            sessionRepository.delete(session.get());
            return Optional.empty();
        }
        return Optional.of(session.get().getIdentity());
    }

    @Transactional
    public void endSession(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteByTokenHash(SecureTokens.hash(token));
        }
    }

    /** Logs a user out everywhere, e.g. after their password is reset. */
    @Transactional
    public void endAllSessions(Identity identity) {
        sessionRepository.deleteByIdentityId(identity.getId());
    }
}
