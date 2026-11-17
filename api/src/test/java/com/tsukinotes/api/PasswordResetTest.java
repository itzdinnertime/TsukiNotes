package com.tsukinotes.api;

import com.tsukinotes.api.auth.SecureTokens;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** "Forgot your password?": the emailed one-time link and setting a new password with it. */
class PasswordResetTest extends IntegrationTest {

    private static final String INVALID_LINK = "This reset link is invalid or has expired. Please request a new one.";

    @Test
    void sendsAnEmailWithALinkToTheWebApp() throws Exception {
        signUp("forgot@example.com");

        MvcResult result = requestReset("Forgot@Example.com");

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        SimpleMailMessage email = awaitEmails("forgot@example.com", 1).get(0);
        assertThat(email.getSubject()).isEqualTo("Reset your TsukiNotes password");
        assertThat(email.getText())
                .contains("Hi Test User")
                .contains("http://localhost:5173/reset-password?token=")
                .contains("expires in 30 minutes");
    }

    @Test
    void unknownEmailGetsTheSameAnswerButNoEmail() throws Exception {
        MvcResult result = requestReset("nobody@example.com");
        awaitBackgroundTasks();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(sentEmails()).isEmpty();
    }

    @Test
    void linkSetsNewPasswordAndWorksOnlyOnce() throws Exception {
        signUp("once@example.com");
        String token = requestResetAndGetToken("once@example.com", 1);

        assertThat(confirm(token, "newpassword1").getResponse().getStatus()).isEqualTo(204);

        assertThat(logIn("once@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(logIn("once@example.com", "newpassword1").getResponse().getStatus()).isEqualTo(200);

        MvcResult again = confirm(token, "anotherpassword1");
        assertThat(again.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(again)).isEqualTo(INVALID_LINK);
    }

    @Test
    void resetLogsOutEverywhere() throws Exception {
        String session = signUp("everywhere@example.com");
        String token = requestResetAndGetToken("everywhere@example.com", 1);

        confirm(token, "newpassword1");

        assertThat(getAs("/auth/me", session).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void tooShortNewPasswordIsRejectedAndTheLinkStaysUsable() throws Exception {
        signUp("short@example.com");
        String token = requestResetAndGetToken("short@example.com", 1);

        MvcResult tooShort = confirm(token, "short");
        assertThat(tooShort.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(tooShort)).isEqualTo("Password must be at least 8 characters");

        assertThat(confirm(token, "longenough1").getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void requestingAgainRetiresTheEarlierLink() throws Exception {
        signUp("twice@example.com");
        String first = requestResetAndGetToken("twice@example.com", 1);
        requestReset("twice@example.com");
        List<SimpleMailMessage> emails = awaitEmails("twice@example.com", 2);
        String second = emails.stream().map(IntegrationTest::resetTokenFrom).filter(t -> !t.equals(first)).findFirst().orElseThrow();

        assertThat(body(confirm(first, "newpassword1"))).isEqualTo(INVALID_LINK);
        assertThat(confirm(second, "newpassword1").getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void atMostThreeEmailsPerAccountPerHour() throws Exception {
        signUp("flood@example.com");

        for (int i = 0; i < 5; i++) {
            assertThat(requestReset("flood@example.com").getResponse().getStatus()).isEqualTo(204);
        }
        awaitBackgroundTasks();

        assertThat(emailsTo("flood@example.com")).hasSize(3);
    }

    @Test
    void simultaneousRequestsStillLeaveOnlyOneWorkingLink() throws Exception {
        signUp("burst@example.com");

        // Requests are handled in the background, so these run at the same time.
        for (int i = 0; i < 5; i++) {
            requestReset("burst@example.com");
        }
        awaitBackgroundTasks();

        Integer created = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
        Integer working = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE used_at IS NULL", Integer.class);
        assertThat(created).isEqualTo(3);
        assertThat(working).isEqualTo(1);
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        signUp("expired@example.com");
        String token = requestResetAndGetToken("expired@example.com", 1);
        jdbc.update("UPDATE password_reset_tokens SET expires_at = created_at - INTERVAL '1 minute'");

        assertThat(body(confirm(token, "newpassword1"))).isEqualTo(INVALID_LINK);
    }

    @Test
    void madeUpTokenIsRejected() throws Exception {
        MvcResult result = confirm("not-a-real-token", "newpassword1");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).isEqualTo(INVALID_LINK);
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        signUp("hash@example.com");
        String token = requestResetAndGetToken("hash@example.com", 1);

        String stored = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class);
        assertThat(stored).isNotEqualTo(token).isEqualTo(SecureTokens.hash(token));
    }

    @Test
    void resetClearsALoginLockout() throws Exception {
        signUp("lockedout@example.com");
        for (int i = 0; i < 5; i++) {
            logIn("lockedout@example.com", "wrong-password");
        }
        assertThat(logIn("lockedout@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(429);

        confirm(requestResetAndGetToken("lockedout@example.com", 1), "freshpassword1");

        assertThat(logIn("lockedout@example.com", "freshpassword1").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void resetEndpointsDontNeedALogin() throws Exception {
        // Checked implicitly above (no session token is sent), but stated explicitly here.
        assertThat(requestReset("anyone@example.com").getResponse().getStatus()).isEqualTo(204);
        assertThat(confirm("x", "newpassword1").getResponse().getStatus()).isEqualTo(400); // not 401
    }

    private MvcResult requestReset(String email) throws Exception {
        return postJson("/auth/password-reset/request", json("email", email), null);
    }

    private MvcResult confirm(String token, String password) throws Exception {
        return postJson("/auth/password-reset/confirm", json("token", token, "password", password), null);
    }

    private String requestResetAndGetToken(String email, int expectedEmailCount) throws Exception {
        requestReset(email);
        return resetTokenFrom(awaitEmails(email, expectedEmailCount).get(expectedEmailCount - 1));
    }
}
