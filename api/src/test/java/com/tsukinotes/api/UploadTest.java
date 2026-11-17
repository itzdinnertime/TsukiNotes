package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Uploading files: automatic tags, validation, the folder rule, and serving files back safely. */
class UploadTest extends IntegrationTest {

    private String token;
    private long folder;

    @BeforeEach
    void setUp() throws Exception {
        token = signUp("uploader@example.com");
        folder = createFolder(token, "CS 101");
    }

    @Test
    void eachFileTypeIsTaggedAutomatically() throws Exception {
        uploadOk(token, "diagram.png", PNG, folder);
        uploadOk(token, "lab1.py", bytes("print('hi')\n"), folder);
        uploadOk(token, "notes.txt", bytes("notes\n"), folder);
        uploadOk(token, "README.md", bytes("# hi\n"), folder);
        uploadOk(token, "app.tsx", bytes("const x = 1;\n"), folder);

        assertThat(tagsOf("diagram.png")).containsExactly("image");
        assertThat(tagsOf("lab1.py")).containsExactlyInAnyOrder("code", "python");
        assertThat(tagsOf("notes.txt")).containsExactly("text");
        assertThat(tagsOf("README.md")).containsExactly("markdown");
        assertThat(tagsOf("app.tsx")).containsExactlyInAnyOrder("code", "typescript");
    }

    @Test
    void userTagsAreAddedWithoutDuplicatingTheAutomaticOnes() throws Exception {
        MvcResult result = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/items/upload")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "hw.py", "text/plain", bytes("x = 1\n")))
                .param("folderIds", String.valueOf(folder))
                .param("tags", "homework, python ,"), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        assertThat(tagsOf("hw.py")).containsExactlyInAnyOrder("code", "python", "homework");
    }

    @Test
    void everyUploadMustGoInAFolder() throws Exception {
        long before = storedFileCount();

        for (String name : List.of("a.png", "a.txt", "a.py")) {
            byte[] content = name.endsWith(".png") ? PNG : bytes("x\n");
            MvcResult result = upload(token, name, content);
            assertThat(result.getResponse().getStatus()).as(name).isEqualTo(400);
            assertThat(body(result)).isEqualTo("Uploaded files must be put in at least one folder");
        }

        assertThat(storedFileCount()).isEqualTo(before);
    }

    @Test
    void invalidFilesAreRejectedAndLeaveNothingOnDisk() throws Exception {
        long before = storedFileCount();

        assertRejected("fake.png", bytes("not an image"), 415, "That file isn't a valid PNG image");
        assertRejected("evil.py", new byte[]{0, 1, 2, 3}, 415, "That file doesn't look like a text file");
        assertRejected("tool.exe", bytes("MZ"), 415, "Only images (PNG, JPG, GIF, WebP), text files and code files can be uploaded");

        assertThat(storedFileCount()).isEqualTo(before);
    }

    @Test
    void uploadedFileComesBackUnchanged() throws Exception {
        long id = uploadOk(token, "diagram.png", PNG, folder);

        MockHttpServletResponse file = getAs("/items/" + id + "/file", token).getResponse();

        assertThat(file.getStatus()).isEqualTo(200);
        assertThat(file.getContentType()).isEqualTo("image/png");
        assertThat(file.getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void htmlIsServedAsPlainTextSoItCantRun() throws Exception {
        long id = uploadOk(token, "page.html", bytes("<script>alert(1)</script>"), folder);

        MockHttpServletResponse file = getAs("/items/" + id + "/file", token).getResponse();

        assertThat(file.getContentType()).startsWith("text/plain");
        assertThat(file.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void filesAreStoredUnderRandomNamesNotTheUploadedName() throws Exception {
        uploadOk(token, "../../evil.txt", bytes("x\n"), folder);

        String stored = jdbc.queryForObject("SELECT stored_file_name FROM items", String.class);
        String original = jdbc.queryForObject("SELECT original_file_name FROM items", String.class);
        assertThat(stored).matches("[0-9a-f-]{36}");
        assertThat(original).isEqualTo("evil.txt"); // path parts stripped
    }

    @Test
    void reusedTitleGetsANumberInsteadOfFailing() throws Exception {
        uploadOk(token, "lab.py", bytes("v1\n"), folder);
        uploadOk(token, "lab.py", bytes("v2\n"), folder);
        uploadOk(token, "lab.py", bytes("v3\n"), folder);

        List<String> titles = JsonPath.read(body(getAs("/items", token)), "$[*].uniqueKey");
        assertThat(titles).containsExactlyInAnyOrder("lab.py", "lab.py (2)", "lab.py (3)");
    }

    @Test
    void otherUsersCantDownloadYourFiles() throws Exception {
        long id = uploadOk(token, "secret.txt", bytes("secret\n"), folder);
        String other = signUp("other@example.com");

        MvcResult result = getAs("/items/" + id + "/file", other);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result)).isEqualTo("File not found");
    }

    @Test
    void cantUploadIntoSomeoneElsesFolder() throws Exception {
        String other = signUp("other@example.com");
        long before = storedFileCount();

        MvcResult result = upload(other, "x.txt", bytes("x\n"), folder);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).isEqualTo("One of the selected folders doesn't exist");
        assertThat(storedFileCount()).isEqualTo(before);
    }

    private void assertRejected(String name, byte[] content, int status, String message) throws Exception {
        MvcResult result = upload(token, name, content, folder);
        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(status);
        assertThat(body(result)).isEqualTo(message);
    }

    private List<String> tagsOf(String title) throws Exception {
        return JsonPath.read(body(getAs("/items", token)), "$[?(@.uniqueKey == '" + title + "')].tags[*]");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
