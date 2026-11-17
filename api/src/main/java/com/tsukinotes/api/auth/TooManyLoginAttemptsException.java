package com.tsukinotes.api.auth;

/** Thrown when an email or IP address has failed to log in too many times recently. Mapped to HTTP 429. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super(message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String message(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return "Too many failed login attempts. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }
}
