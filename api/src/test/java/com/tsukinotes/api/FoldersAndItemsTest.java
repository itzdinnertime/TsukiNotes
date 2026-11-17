package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Folders, moving and deleting items, the My files search, and that users only ever see their own things. */
class FoldersAndItemsTest extends IntegrationTest {

    private String token;
    private long cs;
    private long math;

    @BeforeEach
    void setUp() throws Exception {
        token = signUp("student@example.com");
        cs = createFolder(token, "CS 101");
        math = createFolder(token, "Math 200");
    }

    // ---------- Folders ----------

    @Test
    void foldersAreListedAlphabeticallyWithCounts() throws Exception {
        createFolder(token, "art");
        uploadOk(token, "a.txt", bytes("a"), cs);
        uploadOk(token, "b.txt", bytes("b"), cs, math);

        String body = body(getAs("/folders", token));

        assertThat(JsonPath.<List<String>>read(body, "$[*].name")).containsExactly("art", "CS 101", "Math 200");
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].itemCount")).containsExactly(0, 2, 1);
    }

    @Test
    void folderNamesMustBeUniqueIgnoringCapitals() throws Exception {
        MvcResult duplicate = postJson("/folders", json("name", "cs 101"), token);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate)).isEqualTo("You already have a folder called \"cs 101\"");

        assertThat(body(postJson("/folders", json("name", "   "), token))).isEqualTo("Please enter a folder name");
    }

    @Test
    void renameFolder() throws Exception {
        assertThat(rename(cs, "  Intro to CS  ").getResponse().getStatus()).isEqualTo(200);
        assertThat(folderNames()).contains("Intro to CS");

        assertThat(rename(cs, "INTRO TO CS").getResponse().getStatus()).as("changing only capitals").isEqualTo(200);
        assertThat(body(rename(cs, "math 200"))).isEqualTo("You already have a folder called \"math 200\"");
    }

    @Test
    void deletingAFolderKeepsItsFiles() throws Exception {
        long id = uploadOk(token, "notes.txt", bytes("n"), cs, math);

        assertThat(call(delete("/folders/" + math), token).getResponse().getStatus()).isEqualTo(204);

        assertThat(folderNames()).containsExactly("CS 101");
        assertThat(folderIdsOf(id)).containsExactly((int) cs);
    }

    @Test
    void cantDeleteTheOnlyFolderAFileIsIn() throws Exception {
        uploadOk(token, "only-here.txt", bytes("x"), math);

        MvcResult result = call(delete("/folders/" + math), token);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result)).isEqualTo("1 file is only in \"Math 200\". Move it to another folder or delete it first.");
    }

    // ---------- Moving and deleting items ----------

    @Test
    void moveFileBetweenFolders() throws Exception {
        long id = uploadOk(token, "lab.py", bytes("x"), cs);

        assertThat(setFolders(id, List.of(math)).getResponse().getStatus()).isEqualTo(204);
        assertThat(folderIdsOf(id)).containsExactly((int) math);

        setFolders(id, List.of(cs, math));
        assertThat(folderIdsOf(id)).containsExactlyInAnyOrder((int) cs, (int) math);
    }

    @Test
    void uploadedFileCantBeRemovedFromItsLastFolder() throws Exception {
        long id = uploadOk(token, "lab.py", bytes("x"), cs);

        MvcResult result = setFolders(id, List.of());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).isEqualTo("Uploaded files must be put in at least one folder");
    }

    @Test
    void linksAndNotesDontNeedAFolder() throws Exception {
        long id = createLink("Course site");
        setFolders(id, List.of(cs));

        assertThat(setFolders(id, List.of()).getResponse().getStatus()).isEqualTo(204);
        assertThat(folderIdsOf(id)).isEmpty();
    }

    @Test
    void deletingAFileRemovesItFromDiskAndFromItsFolders() throws Exception {
        long id = uploadOk(token, "gone.txt", bytes("x"), cs);
        long before = storedFileCount();

        assertThat(call(delete("/items/" + id), token).getResponse().getStatus()).isEqualTo(204);

        assertThat(storedFileCount()).isEqualTo(before - 1);
        assertThat(JsonPath.<List<Object>>read(body(getAs("/items", token)), "$")).isEmpty();
        assertThat(JsonPath.<List<Integer>>read(body(getAs("/folders", token)), "$[*].itemCount")).containsOnly(0);

        MvcResult again = call(delete("/items/" + id), token);
        assertThat(again.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(again)).isEqualTo("Item not found");
    }

    @Test
    void deletingATaggedLinkWorks() throws Exception {
        long id = createLink("Tagged");

        assertThat(call(delete("/items/" + id), token).getResponse().getStatus()).isEqualTo(204);
    }

    // ---------- Search ----------

    @Test
    void searchMatchesTitleFilenameLinkTextAndTags() throws Exception {
        uploadOk(token, "lab1.py", bytes("x"), cs);
        uploadOk(token, "calc-notes.txt", bytes("x"), math);
        createLink("Course site");

        assertThat(search("LAB1", null)).containsExactly("lab1.py");             // title, any capitals
        assertThat(search("calc-notes", null)).containsExactly("calc-notes.txt"); // filename
        assertThat(search("python", null)).containsExactly("lab1.py");            // automatic tag
        assertThat(search("example.edu", null)).containsExactly("Course site");   // link text
        assertThat(search("%", null)).isEmpty();                                  // treated literally, not a wildcard
    }

    @Test
    void searchAndFolderFilterCombine() throws Exception {
        uploadOk(token, "lab1.py", bytes("x"), cs);
        uploadOk(token, "lab-report.txt", bytes("x"), math);

        assertThat(search("lab", null)).containsExactlyInAnyOrder("lab1.py", "lab-report.txt");
        assertThat(search("lab", cs)).containsExactly("lab1.py");
        assertThat(search("", math)).containsExactly("lab-report.txt");
    }

    @Test
    void tagSearchNeedsAllTags() throws Exception {
        uploadOk(token, "a.py", bytes("x"), cs);
        uploadOk(token, "b.txt", bytes("x"), cs);

        List<String> both = JsonPath.read(body(getAs("/search?tags=code&tags=python", token)), "$[*].uniqueKey");
        List<String> none = JsonPath.read(body(getAs("/search?tags=code&tags=text", token)), "$[*].uniqueKey");

        assertThat(both).containsExactly("a.py");
        assertThat(none).isEmpty();
    }

    // ---------- Other users ----------

    @Test
    void otherUsersCantSeeOrChangeAnything() throws Exception {
        long file = uploadOk(token, "private.txt", bytes("x"), cs);
        String other = signUp("intruder@example.com");

        assertThat(JsonPath.<List<Object>>read(body(getAs("/items", other)), "$")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(body(getAs("/items?q=private", other)), "$")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(body(getAs("/items?folderId=" + cs, other)), "$")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(body(getAs("/search?tags=text", other)), "$")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(body(getAs("/folders", other)), "$")).isEmpty();

        assertThat(body(call(delete("/items/" + file), other))).isEqualTo("Item not found");
        assertThat(body(call(put("/items/" + file + "/folders").contentType(MediaType.APPLICATION_JSON)
                .content(json("folderIds", List.of())), other))).isEqualTo("Item not found");
        assertThat(body(call(patch("/folders/" + cs).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "mine")), other))).isEqualTo("Folder not found");
        assertThat(body(call(delete("/folders/" + cs), other))).isEqualTo("Folder not found");

        // And the owner's things are untouched.
        assertThat(search("private", null)).containsExactly("private.txt");
        assertThat(folderNames()).containsExactly("CS 101", "Math 200");
    }

    @Test
    void cantMoveYourItemIntoSomeoneElsesFolder() throws Exception {
        String other = signUp("other@example.com");
        long otherFolder = createFolder(other, "Theirs");
        long mine = uploadOk(token, "mine.txt", bytes("x"), cs);

        MvcResult result = setFolders(mine, List.of(otherFolder));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).isEqualTo("One of the selected folders doesn't exist");
    }

    // ---------- Helpers ----------

    private long createLink(String title) throws Exception {
        MvcResult result = postJson("/items",
                json("uniqueKey", title, "type", "link", "content", "https://example.edu/cs101", "tags", List.of("web")), token);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private MvcResult rename(long folderId, String name) throws Exception {
        return call(patch("/folders/" + folderId).contentType(MediaType.APPLICATION_JSON).content(json("name", name)), token);
    }

    private MvcResult setFolders(long itemId, List<Long> folderIds) throws Exception {
        return call(put("/items/" + itemId + "/folders").contentType(MediaType.APPLICATION_JSON)
                .content(json("folderIds", folderIds)), token);
    }

    private List<String> folderNames() throws Exception {
        return JsonPath.read(body(getAs("/folders", token)), "$[*].name");
    }

    private List<Integer> folderIdsOf(long itemId) throws Exception {
        return JsonPath.read(body(getAs("/items", token)), "$[?(@.id == " + itemId + ")].folderIds[*]");
    }

    private List<String> search(String query, Long folderId) throws Exception {
        String path = "/items?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
                + (folderId == null ? "" : "&folderId=" + folderId);
        return JsonPath.read(body(getAs(path, token)), "$[*].uniqueKey");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
