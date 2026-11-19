package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import com.tsukinotes.api.files.ItemTextBackfill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The My files search also looks inside uploaded text and code files. */
class TextSearchTest extends IntegrationTest {

    @Autowired
    private ItemTextBackfill backfill;

    private String token;
    private long cs;

    @BeforeEach
    void setUp() throws Exception {
        token = signUp("a@example.com");
        cs = createFolder(token, "CS 101");
    }

    @Test
    void findsAFileByWordsOnlyInsideIt() throws Exception {
        uploadOk(token, "lab1.py", bytes("def quicksort(items):\n    return sorted(items)\n"), cs);
        uploadOk(token, "notes.txt", bytes("Nothing relevant here"), cs);

        assertThat(titles(search("QUICKSORT"))).containsExactly("lab1.py"); // any capitals
    }

    @Test
    void showsAnExtractAroundTheMatch() throws Exception {
        String before = "a".repeat(200);
        String after = "b".repeat(200);
        uploadOk(token, "long.txt", bytes(before + "\n\n  the   needle  is here\n" + after), cs);

        String snippet = (String) search("needle").get(0).get("textMatch");

        assertThat(snippet).startsWith("…").endsWith("…");
        assertThat(snippet).contains(" the needle is here "); // line breaks and runs of spaces collapsed
        assertThat(snippet.length()).isLessThan(150);
    }

    @Test
    void noExtractWhenOnlyTheTitleMatched() throws Exception {
        uploadOk(token, "lab1.py", bytes("print('hi')"), cs);

        Map<String, Object> result = search("lab1").get(0);

        assertThat(result.get("textMatch")).isNull();
    }

    @Test
    void percentAndUnderscoreAreMatchedLiterally() throws Exception {
        uploadOk(token, "a.txt", bytes("snake_case here"), cs);
        uploadOk(token, "b.txt", bytes("snakeXcase and 100 percent, 50% off"), cs);

        assertThat(titles(search("snake_case"))).containsExactly("a.txt"); // "_" isn't "any character"
        assertThat(titles(search("100%"))).isEmpty();                     // "%" isn't "anything"
        assertThat(titles(search("50% off"))).containsExactly("b.txt");    // but is found when really there
    }

    @Test
    void combinesWithTheFolderFilter() throws Exception {
        long math = createFolder(token, "Math");
        uploadOk(token, "cs.txt", bytes("integral"), cs);
        uploadOk(token, "math.txt", bytes("integral"), math);

        List<Map<String, Object>> inMath = JsonPath.read(
                body(getAs("/items?q=integral&folderId=" + math, token)), "$");

        assertThat(titles(inMath)).containsExactly("math.txt");
    }

    @Test
    void otherUsersFileTextIsNeverSearched() throws Exception {
        uploadOk(token, "secret.txt", bytes("my secret recipe"), cs);
        String other = signUp("b@example.com");

        List<Map<String, Object>> found = JsonPath.read(body(getAs("/items?q=recipe", other)), "$");

        assertThat(found).isEmpty();
    }

    @Test
    void deletingAFileDeletesItsSearchableText() throws Exception {
        long id = uploadOk(token, "lab1.py", bytes("print('hi')"), cs);

        call(delete("/items/" + id), token);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_texts", Integer.class)).isZero();
    }

    @Test
    void aShortFileIsShownWholeWithoutEllipses() throws Exception {
        uploadOk(token, "short.txt", bytes("needle at the start"), cs);

        assertThat(search("needle").get(0).get("textMatch")).isEqualTo("needle at the start");
    }

    @Test
    void imagesHaveNoSearchableText() throws Exception {
        uploadOk(token, "photo.png", PNG, cs);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_texts", Integer.class)).isZero();
    }

    @Test
    void aByteOrderMarkDoesntSplitTheFirstWord() throws Exception {
        uploadOk(token, "bom.txt", bytes((char) 0xFEFF + "hello world"), cs); // 0xFEFF = byte-order mark

        assertThat(search("hello").get(0).get("textMatch")).isEqualTo("hello world");
    }

    @Test
    void startupFillInSkipsMissingFilesAndDoesTheRest() throws Exception {
        long gone = uploadOk(token, "gone.txt", bytes("shared word"), cs);
        uploadOk(token, "kept.txt", bytes("shared word"), cs);
        jdbc.update("DELETE FROM item_texts");
        String storedName = jdbc.queryForObject("SELECT stored_file_name FROM items WHERE id = ?", String.class, gone);
        Files.delete(STORAGE_DIR.resolve(storedName));

        backfill.run(null); // must not throw

        assertThat(titles(search("shared word"))).containsExactly("kept.txt");
    }

    @Test
    void filesUploadedBeforeThisFeatureAreMadeSearchableOnStartup() throws Exception {
        uploadOk(token, "old.txt", bytes("uploaded long ago"), cs);
        jdbc.update("DELETE FROM item_texts"); // as if it was uploaded before item_texts existed
        assertThat(search("long ago")).isEmpty();

        backfill.run(null);

        assertThat(titles(search("long ago"))).containsExactly("old.txt");
    }

    private List<Map<String, Object>> search(String query) throws Exception {
        return JsonPath.read(body(call(get("/items").param("q", query), token)), "$");
    }

    private static List<Object> titles(List<Map<String, Object>> items) {
        return items.stream().map(item -> item.get("uniqueKey")).toList();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
