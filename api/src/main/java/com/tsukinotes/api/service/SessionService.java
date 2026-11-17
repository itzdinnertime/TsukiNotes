package com.tsukinotes.api.service;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.domain.Session;
import com.tsukinotes.api.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SessionService {

    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final int TOKEN_BYTES = 32;

    private final SessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** Starts a session and returns the raw token. Only its hash is stored. */
    public String createSession(Identity identity) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now();
        Session session = new Session();
        session.setIdentity(identity);
        session.setTokenHash(hash(token));
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

        Optional<Session> session = sessionRepository.findByTokenHash(hash(token));
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
            sessionRepository.deleteByTokenHash(hash(token));
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
