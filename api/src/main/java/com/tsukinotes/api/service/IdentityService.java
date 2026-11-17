package com.tsukinotes.api.service;

import com.tsukinotes.api.auth.LoginAttemptService;
import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.repository.IdentityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Service
public class IdentityService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt only uses the first 72 bytes of a password.
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final IdentityRepository identityRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public IdentityService(IdentityRepository identityRepository, LoginAttemptService loginAttemptService) {
        this.identityRepository = identityRepository;
        this.loginAttemptService = loginAttemptService;
    }

    public Identity signUp(String name, String email, String password) {
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = normalizeEmail(email);

        if (cleanName.isEmpty()) {
            throw badRequest("Please enter your name");
        }
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw badRequest("Please enter a valid email address");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw badRequest("Password must be at most " + MAX_PASSWORD_LENGTH + " characters");
        }
        if (identityRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }

        Identity identity = new Identity();
        identity.setName(cleanName);
        identity.setEmail(cleanEmail);
        identity.setPasswordHash(passwordEncoder.encode(password));
        return identityRepository.save(identity);
    }

    /**
     * Checks the email and password, enforcing the failed-attempt limits.
     * A locked-out email or IP is refused before the password is even checked,
     * so guessing can't continue during a lockout.
     */
    public Identity logIn(String email, String password, String ipAddress) {
        String cleanEmail = normalizeEmail(email);
        loginAttemptService.checkAllowed(cleanEmail, ipAddress);

        Identity identity = identityRepository.findByEmailIgnoreCase(cleanEmail).orElse(null);

        if (identity == null
                || identity.getPasswordHash() == null
                || password == null
                || !passwordEncoder.matches(password, identity.getPasswordHash())) {
            int left = loginAttemptService.recordFailure(cleanEmail, ipAddress);
            // Same message whether or not the account exists, so we don't reveal which emails have accounts.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, failureMessage(left));
        }

        loginAttemptService.clearFailures(cleanEmail);
        return identity;
    }

    private String failureMessage(int attemptsLeft) {
        String message = "Incorrect email or password";
        long minutes = loginAttemptService.getWindow().toMinutes();
        if (attemptsLeft == 0) {
            return message + ". Too many failed attempts: try again in " + minutes + " minutes.";
        }
        if (attemptsLeft <= 2) {
            return message + ". " + attemptsLeft + (attemptsLeft == 1 ? " attempt" : " attempts")
                    + " left before logins are paused for " + minutes + " minutes.";
        }
        return message;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
