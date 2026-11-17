package com.tsukinotes.api.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends the app's emails over SMTP (settings under spring.mail in application.yaml).
 * Sending runs in the background so a slow mail server never slows down, or changes the timing of,
 * the request that triggered it.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String host;
    private final int port;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from}") String from,
                       @Value("${spring.mail.host}") String host,
                       @Value("${spring.mail.port}") int port) {
        this.mailSender = mailSender;
        this.from = from;
        this.host = host;
        this.port = port;
    }

    /**
     * On startup, say plainly where emails will go, and check the mail server can be reached and
     * accepts the login, so a wrong password shows up now instead of as "the email never came".
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void reportMailSetup() {
        boolean mailpit = ("localhost".equals(host) || "127.0.0.1".equals(host)) && port == 1025;
        String where = mailpit
                ? "the local Mailpit server: emails are CAUGHT, not delivered; read them at http://localhost:8025"
                : host + ":" + port + " (real delivery)";

        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            log.info("Emails go to {}", where);
            return;
        }
        try {
            impl.testConnection();
            log.info("Emails go to {}. Connection check OK; sending as {}", where, from);
        } catch (MessagingException e) {
            String hint = host.contains("gmail")
                    ? " For Gmail, the password must be an app password (https://myaccount.google.com/apppasswords), not your normal Google password."
                    : mailpit ? " Is Mailpit running? Start it with: docker compose up -d" : "";
            log.warn("Emails go to {}, but the mail server can't be used: {}.{}", where, e.getMessage(), hint);
        }
    }

    @Async
    public void sendPasswordReset(String to, String name, String resetLink, long validMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your TsukiNotes password");
        message.setText("""
                Hi %s,

                Someone (hopefully you) asked to reset the password for your TsukiNotes account.

                To choose a new password, open this link:
                %s

                The link works once and expires in %d minutes.

                If you didn't ask for this, you can ignore this email; your password won't change.
                """.formatted(name, resetLink, validMinutes));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Never log the link itself: it would let anyone reading the logs reset the password.
            log.warn("Could not send password reset email via {}:{}: {}", host, port, e.getMessage());
        }
    }
}
