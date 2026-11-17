package com.tsukinotes.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Limits on repeated failed logins: 5 per email and 20 per IP address, within 15 minutes. */
class LoginLimitsTest extends IntegrationTest {

    private static final String LOCKED = "Too many failed login attempts. Try again in 15 minutes.";

    @Test
    void warnsThenPausesAfterFiveFailures() throws Exception {
        signUp("victim@example.com");

        List<String> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(body(logIn("victim@example.com", "wrong-password")));
        }

        assertThat(messages).containsExactly(
                "Incorrect email or password",
                "Incorrect email or password",
                "Incorrect email or password. 2 attempts left before logins are paused for 15 minutes.",
                "Incorrect email or password. 1 attempt left before logins are paused for 15 minutes.",
                "Incorrect email or password. Too many failed attempts: try again in 15 minutes.");
    }

    @Test
    void whilePausedEvenTheCorrectPasswordIsRefused() throws Exception {
        signUp("paused@example.com");
        failLogins("paused@example.com", 5, "127.0.0.1");

        MvcResult result = logIn("PAUSED@example.com", PASSWORD); // capitals don't get around it

        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(result)).isEqualTo(LOCKED);
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After"))).isBetween(840, 900);
    }

    @Test
    void otherAccountsAreNotAffected() throws Exception {
        signUp("locked@example.com");
        signUp("fine@example.com");
        failLogins("locked@example.com", 5, "127.0.0.1");

        assertThat(logIn("fine@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unknownEmailsBehaveExactlyLikeRealOnes() throws Exception {
        signUp("exists@example.com");

        List<String> real = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            real.add(body(logIn("exists@example.com", "wrong-password")));
            unknown.add(body(logIn("ghost@example.com", "wrong-password")));
        }

        // Same warnings, same lockout: the limits don't reveal which emails have accounts.
        assertThat(unknown).isEqualTo(real);
        assertThat(real.get(5)).isEqualTo(LOCKED);
    }

    @Test
    void successfulLoginResetsTheCount() throws Exception {
        signUp("reset@example.com");
        failLogins("reset@example.com", 4, "127.0.0.1");

        assertThat(logIn("reset@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);

        // Counting starts again from zero: the next failure gets the plain message, not "0 left".
        assertThat(body(logIn("reset@example.com", "wrong-password"))).isEqualTo("Incorrect email or password");
    }

    @Test
    void failuresOlderThanTheWindowDontCount() throws Exception {
        signUp("old@example.com");
        failLogins("old@example.com", 5, "127.0.0.1");
        jdbc.update("UPDATE login_failures SET attempted_at = attempted_at - INTERVAL '16 minutes'");

        assertThat(logIn("old@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void oneAddressTryingManyAccountsIsPausedAfterTwenty() throws Exception {
        signUp("target@example.com");
        String attacker = "10.1.2.3";

        // 20 failures spread over 20 different emails: no single email reaches its own limit.
        for (int i = 0; i < 20; i++) {
            assertThat(logInFrom("spray" + i + "@example.com", "guess12345", attacker).getResponse().getStatus())
                    .isEqualTo(401);
        }

        MvcResult fromAttacker = logInFrom("target@example.com", PASSWORD, attacker);
        assertThat(fromAttacker.getResponse().getStatus()).isEqualTo(429);

        // Someone on a different address can still log in to the same account.
        assertThat(logInFrom("target@example.com", PASSWORD, "10.9.9.9").getResponse().getStatus()).isEqualTo(200);
    }

    private void failLogins(String email, int times, String ip) throws Exception {
        for (int i = 0; i < times; i++) {
            logInFrom(email, "wrong-password", ip);
        }
    }

    private MvcResult logInFrom(String email, String password, String ip) throws Exception {
        return call(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password))
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }), null);
    }
}
