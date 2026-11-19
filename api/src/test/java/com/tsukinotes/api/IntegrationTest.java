package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for tests that call the real API (full app, real PostgreSQL in Docker, MockMvc for HTTP).
 * Every test starts with an empty database. Emails are captured instead of sent.
 *
 * All subclasses share one app context (and so one database container), which keeps the suite fast:
 * don't add per-class configuration that would split it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "password123";

    /** Uploads go to a temporary folder, never api/uploads. */
    protected static final Path STORAGE_DIR = createTempDir();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage-dir", STORAGE_DIR::toString);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Replaces the real mail sender: nothing is sent, and tests can inspect what would have been. */
    @MockitoBean
    protected JavaMailSender mailSender;

    /** Runs @Async work (password reset requests, emails). */
    @Autowired
    @Qualifier("applicationTaskExecutor")
    protected ThreadPoolTaskExecutor backgroundTasks;

    @BeforeEach
    void emptyDatabase() {
        awaitBackgroundTasks();
        // Cascades to sessions, items, item_tags, folders, item_folders and password_reset_tokens.
        jdbc.execute("TRUNCATE identities, tags, login_failures RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void finishBackgroundWork() {
        // So one test's background work can't leak into the next test.
        awaitBackgroundTasks();
    }

    /** Waits until all background (@Async) work has finished. */
    protected void awaitBackgroundTasks() {
        waitUntil(() -> backgroundTasks.getActiveCount() == 0
                        && backgroundTasks.getThreadPoolExecutor().getQueue().isEmpty(),
                Duration.ofSeconds(15), "background tasks to finish");
    }

    // ---------- Requests ----------

    /** Adds the session token, if given. */
    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B withAuth(B request, String sessionToken) {
        if (sessionToken != null) {
            request.header("Authorization", "Bearer " + sessionToken);
        }
        return request;
    }

    protected <B extends AbstractMockHttpServletRequestBuilder<B>> MvcResult call(B request, String sessionToken) throws Exception {
        return mvc.perform(withAuth(request, sessionToken)).andReturn();
    }

    protected MvcResult postJson(String path, String json, String sessionToken) throws Exception {
        return call(post(path).contentType(MediaType.APPLICATION_JSON).content(json), sessionToken);
    }

    protected MvcResult getAs(String path, String sessionToken) throws Exception {
        return call(get(path), sessionToken);
    }

    // ---------- Accounts ----------

    /** Creates an account and returns its session token. */
    protected String signUp(String email) throws Exception {
        MvcResult result = postJson("/auth/signup",
                json("name", "Test User", "email", email, "password", PASSWORD), null);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Sign-up failed: " + body(result));
        }
        return JsonPath.read(body(result), "$.token");
    }

    protected MvcResult logIn(String email, String password) throws Exception {
        return postJson("/auth/login", json("email", email, "password", password), null);
    }

    // ---------- Folders and uploads ----------

    protected long createFolder(String token, String name) throws Exception {
        MvcResult result = postJson("/folders", json("name", name), token);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Creating folder failed: " + body(result));
        }
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    protected MvcResult upload(String token, String fileName, byte[] content, Long... folderIds) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/items/upload")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", content));
        for (Long id : folderIds) {
            request.param("folderIds", String.valueOf(id));
        }
        return call(request, token);
    }

    protected long uploadOk(String token, String fileName, byte[] content, Long... folderIds) throws Exception {
        MvcResult result = upload(token, fileName, content, folderIds);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("Upload failed: " + body(result));
        }
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    protected static long storedFileCount() {
        try (Stream<Path> files = Files.list(STORAGE_DIR)) {
            return files.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A real 1x1 PNG image. */
    protected static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    // ---------- Emails ----------

    /** Emails "sent" so far in this test (they're sent in the background, so this may lag slightly). */
    protected List<SimpleMailMessage> sentEmails() {
        return mockingDetails(mailSender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .flatMap(i -> Stream.of(i.getArguments()))
                .filter(SimpleMailMessage.class::isInstance)
                .map(SimpleMailMessage.class::cast)
                .toList();
    }

    protected List<SimpleMailMessage> emailsTo(String address) {
        return sentEmails().stream().filter(m -> List.of(m.getTo()).contains(address)).toList();
    }

    /** Waits (up to 10 s) until `address` has received `count` emails, then returns them. */
    protected List<SimpleMailMessage> awaitEmails(String address, int count) {
        waitUntil(() -> emailsTo(address).size() >= count, Duration.ofSeconds(10),
                "Expected " + count + " email(s) to " + address);
        return emailsTo(address);
    }

    /** The token from a password reset email's link. */
    protected static String resetTokenFrom(SimpleMailMessage email) {
        var matcher = java.util.regex.Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(email.getText());
        if (!matcher.find()) {
            throw new AssertionError("No reset link in email:\n" + email.getText());
        }
        return matcher.group(1);
    }

    // ---------- Utilities ----------

    protected static String body(MvcResult result) throws IOException {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Builds a small JSON object from key/value pairs (values are strings, numbers or lists). */
    protected static String json(Object... keysAndValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(keysAndValues[i]).append("\":").append(jsonValue(keysAndValues[i + 1]));
        }
        return sb.append('}').toString();
    }

    private static String jsonValue(Object value) {
        if (value instanceof Number) return value.toString();
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(IntegrationTest::jsonValue).toList()) + "]";
        }
        return '"' + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    protected static void waitUntil(BooleanSupplier condition, Duration timeout, String description) {
        long end = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("Timed out: " + description);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("tsukinotes-test-uploads");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
