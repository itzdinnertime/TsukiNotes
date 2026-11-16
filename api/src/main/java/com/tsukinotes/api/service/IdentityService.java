package com.tsukinotes.api.service;

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

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\s]+@[^@\s]+\.[^@\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt only uses the first 72 bytes of a password.
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final IdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public IdentityService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
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

    public Identity logIn(String email, String password) {
        Identity identity = identityRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElse(null);

        if (identity == null
                || identity.getPasswordHash() == null
                || password == null
                || !passwordEncoder.matches(password, identity.getPasswordHash())) {
            // Same message either way so we don't reveal which emails have accounts.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }

        return identity;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
