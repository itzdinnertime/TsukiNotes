package com.tsukinotes.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Limits repeated failed logins, counted over a rolling window:
 * per email (stops guessing one account's password) and per IP address
 * (stops one machine trying a password against many accounts).
 * The limits apply whether or not the email has an account, so they don't reveal which emails exist.
 */
@Service
public class LoginAttemptService {

    private static final Duration CLEANUP_AGE = Duration.ofDays(1);

    private final JdbcTemplate jdbc;
    private final int maxFailuresPerEmail;
    private final int maxFailuresPerIp;
    private final Duration window;

    public LoginAttemptService(JdbcTemplate jdbc,
                               @Value("${app.login-limits.max-failures-per-email}") int maxFailuresPerEmail,
                               @Value("${app.login-limits.max-failures-per-ip}") int maxFailuresPerIp,
                               @Value("${app.login-limits.window}") Duration window) {
        this.jdbc = jdbc;
        this.maxFailuresPerEmail = maxFailuresPerEmail;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = window;
    }

    /** Throws if this email or IP is currently locked out. Call before checking the password. */
    public void checkAllowed(String email, String ip) {
        LocalDateTime since = LocalDateTime.now().minus(window);
        long wait = Math.max(
                secondsUntilAllowed("email", email, maxFailuresPerEmail, since),
                secondsUntilAllowed("ip_address", ip, maxFailuresPerIp, since));
        if (wait > 0) {
            throw new TooManyLoginAttemptsException(wait);
        }
    }

    /** Records a failed attempt and returns how many tries this email has left before being locked out. */
    public int recordFailure(String email, String ip) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO login_failures (email, ip_address, attempted_at) VALUES (?, ?, ?)",
                email, ip, Timestamp.valueOf(now));
        jdbc.update("DELETE FROM login_failures WHERE attempted_at < ?", Timestamp.valueOf(now.minus(CLEANUP_AGE)));

        long recent = countSince("email", email, now.minus(window));
        return (int) Math.max(0, maxFailuresPerEmail - recent);
    }

    /** A successful login wipes that email's failures (but not the IP's). */
    public void clearFailures(String email) {
        jdbc.update("DELETE FROM login_failures WHERE email = ?", email);
    }

    public Duration getWindow() {
        return window;
    }

    /**
     * 0 if allowed. Otherwise the seconds until enough of the recent failures fall out of the window
     * to allow another try (the rolling window means the oldest counted failure decides).
     */
    private long secondsUntilAllowed(String column, String value, int max, LocalDateTime since) {
        if (countSince(column, value, since) < max) {
            return 0;
        }
        // The failure that has to expire is the max-th most recent one.
        Timestamp deciding = jdbc.queryForObject(
                "SELECT attempted_at FROM login_failures WHERE " + column + " = ? AND attempted_at >= ? "
                        + "ORDER BY attempted_at DESC OFFSET ? LIMIT 1",
                Timestamp.class, value, Timestamp.valueOf(since), max - 1);
        LocalDateTime allowedAt = deciding.toLocalDateTime().plus(window);
        return Math.max(1, Duration.between(LocalDateTime.now(), allowedAt).toSeconds());
    }

    private long countSince(String column, String value, LocalDateTime since) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_failures WHERE " + column + " = ? AND attempted_at >= ?",
                Long.class, value, Timestamp.valueOf(since));
        return count == null ? 0 : count;
    }
}
