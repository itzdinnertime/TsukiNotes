package com.tsukinotes.api.service;

import com.tsukinotes.api.auth.LoginAttemptService;
import com.tsukinotes.api.auth.SecureTokens;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.mail.MailService;
import com.tsukinotes.api.repository.IdentityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * "Forgot password" flow: email a one-time link, then let the link's holder set a new password.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration CLEANUP_AGE = Duration.ofDays(1);
    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final IdentityService identityService;
    private final IdentityRepository identityRepository;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final MailService mailService;
    private final String frontendUrl;
    private final Duration tokenLifetime;
    private final int maxEmailsPerHour;

    public PasswordResetService(JdbcTemplate jdbc,
                                IdentityService identityService,
                                IdentityRepository identityRepository,
                                SessionService sessionService,
                                LoginAttemptService loginAttemptService,
                                MailService mailService,
                                @Value("${app.frontend-url}") String frontendUrl,
                                @Value("${app.password-reset.token-lifetime}") Duration tokenLifetime,
                                @Value("${app.password-reset.max-emails-per-hour}") int maxEmailsPerHour) {
        this.jdbc = jdbc;
        this.identityService = identityService;
        this.identityRepository = identityRepository;
        this.sessionService = sessionService;
        this.loginAttemptService = loginAttemptService;
        this.mailService = mailService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.tokenLifetime = tokenLifetime;
        this.maxEmailsPerHour = maxEmailsPerHour;
    }

    /**
     * Emails a reset link if the email belongs to an account. Deliberately looks the same to the
     * caller whether or not it does: no error, no difference in what's returned, and all the work
     * (including the account lookup) runs in the background, so the response time doesn't give it
     * away either. Problems are logged, never reported back.
     */
    @Async
    @Transactional
    public void requestReset(String email) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("DELETE FROM password_reset_tokens WHERE created_at < ?", Timestamp.valueOf(now.minus(CLEANUP_AGE)));

        Identity identity = identityService.findAccountByEmail(email).orElse(null);
        if (identity == null) {
            return;
        }

        // Requests run in the background, so two for the same account can run at once. Locking the
        // account row makes them take turns; otherwise both could slip past the hourly limit, and
        // both could create a link before either retires the other, leaving two working links.
        jdbc.queryForObject("SELECT id FROM identities WHERE id = ? FOR UPDATE", Long.class, identity.getId());

        // Stop anyone using this form to flood someone's inbox.
        Long recent = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE identity_id = ? AND created_at >= ?",
                Long.class, identity.getId(), Timestamp.valueOf(now.minus(RATE_WINDOW)));
        if (recent != null && recent >= maxEmailsPerHour) {
            log.info("Password reset email limit reached for account {}", identity.getId());
            return;
        }

        // Only the newest link works: retire any earlier unused ones.
        jdbc.update("UPDATE password_reset_tokens SET used_at = ? WHERE identity_id = ? AND used_at IS NULL",
                Timestamp.valueOf(now), identity.getId());

        String token = SecureTokens.generate();
        jdbc.update("INSERT INTO password_reset_tokens (identity_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?)",
                identity.getId(), SecureTokens.hash(token), Timestamp.valueOf(now), Timestamp.valueOf(now.plus(tokenLifetime)));

        String link = frontendUrl + "/reset-password?token=" + token;
        String to = identity.getEmail();
        String name = identity.getName();
        // Send only once the token is safely saved, so nobody gets a link that doesn't work.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailService.sendPasswordReset(to, name, link, tokenLifetime.toMinutes());
            }
        });
    }

    /**
     * Sets a new password using a link's token. The token works once; afterwards the user is logged out
     * everywhere (in case someone else had access) and any login lockout on the email is cleared.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw invalidLink();
        }

        // FOR UPDATE: if the same link is submitted twice at once, the second waits and then sees it used.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, identity_id FROM password_reset_tokens "
                        + "WHERE token_hash = ? AND used_at IS NULL AND expires_at > ? FOR UPDATE",
                SecureTokens.hash(token), Timestamp.valueOf(LocalDateTime.now()));
        if (rows.isEmpty()) {
            throw invalidLink();
        }
        long tokenId = ((Number) rows.get(0).get("id")).longValue();
        long identityId = ((Number) rows.get(0).get("identity_id")).longValue();

        Identity identity = identityRepository.findById(identityId).orElseThrow(PasswordResetService::invalidLink);
        identityService.changePassword(identity, newPassword); // throws on a too-short/long password; link stays usable

        jdbc.update("UPDATE password_reset_tokens SET used_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), tokenId);
        sessionService.endAllSessions(identity);
        loginAttemptService.clearFailures(identity.getEmail());
    }

    public Duration getTokenLifetime() {
        return tokenLifetime;
    }

    private static ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has expired. Please request a new one.");
    }
}
