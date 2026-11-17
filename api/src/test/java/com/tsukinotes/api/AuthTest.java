package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Sign-up, log-in, sessions and log-out. */
class AuthTest extends IntegrationTest {

    @Test
    void signUpReturnsTokenAndUserWithoutPasswordHash() throws Exception {
        MvcResult result = postJson("/auth/signup",
                json("name", "Ada", "email", "Ada@Example.com ", "password", PASSWORD), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = body(result);
        assertThat((String) JsonPath.read(body, "$.token")).hasSizeGreaterThan(30);
        assertThat((String) JsonPath.read(body, "$.identity.email")).isEqualTo("ada@example.com"); // trimmed, lower-cased
        assertThat(body).doesNotContain("password").doesNotContain("$2a$"); // never leak the bcrypt hash
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() throws Exception {
        signUp("hash@example.com");

        String stored = jdbc.queryForObject("SELECT password_hash FROM identities", String.class);
        assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void signUpValidation() throws Exception {
        assertSignUpFails("", "a@example.com", PASSWORD, 400, "Please enter your name");
        assertSignUpFails("A", "not-an-email", PASSWORD, 400, "Please enter a valid email address");
        assertSignUpFails("A", "a@example.com", "short", 400, "Password must be at least 8 characters");
        assertSignUpFails("A", "a@example.com", "x".repeat(73), 400, "Password must be at most 72 characters");

        signUp("taken@example.com");
        assertSignUpFails("B", "TAKEN@example.com", PASSWORD, 409, "An account with that email already exists");
    }

    @Test
    void logInWithCorrectPasswordAnyEmailCapitalisation() throws Exception {
        signUp("login@example.com");

        MvcResult result = logIn("LOGIN@Example.com", PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(body(result), "$.identity.email")).isEqualTo("login@example.com");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameMessage() throws Exception {
        signUp("real@example.com");

        MvcResult wrongPassword = logIn("real@example.com", "wrong-password");
        MvcResult unknownEmail = logIn("nobody@example.com", "wrong-password");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(wrongPassword)).isEqualTo("Incorrect email or password").isEqualTo(body(unknownEmail));
    }

    @Test
    void protectedEndpointsNeedAValidSessionToken() throws Exception {
        String token = signUp("me@example.com");

        assertThat(getAs("/auth/me", token).getResponse().getStatus()).isEqualTo(200);

        for (String path : new String[]{"/auth/me", "/items", "/folders", "/search?tags=x"}) {
            MvcResult noToken = getAs(path, null);
            assertThat(noToken.getResponse().getStatus()).as(path + " without token").isEqualTo(401);
            assertThat(body(noToken)).isEqualTo("Please log in again");

            assertThat(getAs(path, "made-up-token").getResponse().getStatus()).as(path + " with fake token").isEqualTo(401);
        }
    }

    @Test
    void sessionTokensAreStoredOnlyAsHashes() throws Exception {
        String token = signUp("hashes@example.com");

        Integer rawStored = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE token_hash = ?", Integer.class, token);
        assertThat(rawStored).isZero();
    }

    @Test
    void expiredSessionIsRejected() throws Exception {
        String token = signUp("expired@example.com");
        jdbc.update("UPDATE sessions SET expires_at = created_at - INTERVAL '1 minute'");

        assertThat(getAs("/auth/me", token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logOutEndsOnlyThatSession() throws Exception {
        String first = signUp("logout@example.com");
        String second = JsonPath.read(body(logIn("logout@example.com", PASSWORD)), "$.token");

        MvcResult logout = call(post("/auth/logout"), first);

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(getAs("/auth/me", first).getResponse().getStatus()).isEqualTo(401);
        assertThat(getAs("/auth/me", second).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void everyRequestNeedsTheInviteToken() throws Exception {
        String token = signUp("invite@example.com");

        int status = mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    private void assertSignUpFails(String name, String email, String password, int status, String message) throws Exception {
        MvcResult result = postJson("/auth/signup", json("name", name, "email", email, "password", password), null);
        assertThat(result.getResponse().getStatus()).as(message).isEqualTo(status);
        assertThat(body(result)).isEqualTo(message);
    }
}
