package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * item_search_view is a stored copy of each item's search row, kept up to date one item at a time
 * by triggers. These check it never goes stale: every way the underlying data can change must
 * show up in searches straight away, and the stored rows must always equal a fresh computation.
 */
class SearchViewTest extends IntegrationTest {

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void isATableWithAGinIndexOnTags() {
        String kind = jdbc.queryForObject("SELECT relkind::text FROM pg_class WHERE relname = 'item_search_view'", String.class);
        String tagsIndex = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'item_search_view_tags_idx'", String.class);

        assertThat(kind).isEqualTo("r"); // an ordinary table, not a (materialized) view
        assertThat(tagsIndex).containsIgnoringCase("USING gin (tags)");
    }

    @Test
    void storedRowsAlwaysMatchAFreshComputation() throws Exception {
        String token = signUp("a@example.com");
        long cs = createFolder(token, "CS 101");
        long math = createFolder(token, "Math");
        long lab = uploadOk(token, "lab1.py", "x".getBytes(StandardCharsets.UTF_8), cs, math);
        long notes = uploadOk(token, "notes.txt", "x".getBytes(StandardCharsets.UTF_8), cs);
        assertMatchesFreshComputation();

        call(put("/items/" + lab + "/folders").contentType(MediaType.APPLICATION_JSON).content(json("folderIds", List.of(math))), token);
        assertMatchesFreshComputation();

        call(delete("/folders/" + cs), token); // refused: notes.txt is only in CS 101
        call(delete("/items/" + notes), token);
        call(delete("/folders/" + cs), token);
        assertMatchesFreshComputation();

        jdbc.update("UPDATE tags SET name = 'py' WHERE name = 'python'"); // renames it on every item
        assertMatchesFreshComputation();
        assertThat(JsonPath.<List<String>>read(body(getAs("/search?tags=py", token)), "$[*].uniqueKey"))
                .containsExactly("lab1.py");

        jdbc.execute("TRUNCATE item_folders");
        assertMatchesFreshComputation();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_search_view", Integer.class)).isEqualTo(1);
    }

    @Test
    void twoTransactionsTaggingTheSameItemAtOnceKeepBothTags() throws Exception {
        String token = signUp("a@example.com");
        long id = uploadOk(token, "notes.txt", "x".getBytes(StandardCharsets.UTF_8), createFolder(token, "CS 101"));
        jdbc.update("INSERT INTO tags (name) VALUES ('first'), ('second')");

        CountDownLatch firstTagged = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = threads.submit(() -> transactions.executeWithoutResult(status -> {
                addTag(id, "first");
                firstTagged.countDown();
                await(secondStarted);
                sleep(300); // give the second one time to reach the item and have to wait for us
            }));
            Future<?> second = threads.submit(() -> {
                await(firstTagged);
                secondStarted.countDown();
                transactions.executeWithoutResult(status -> addTag(id, "second"));
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }

        List<String> tags = JsonPath.read(body(getAs("/items", token)), "$[0].tags");
        assertThat(tags).contains("first", "second");
        assertMatchesFreshComputation();
    }

    private void addTag(long itemId, String tag) {
        jdbc.update("INSERT INTO item_tags (item_id, tag_id) SELECT ?, id FROM tags WHERE name = ?", itemId, tag);
    }

    private void assertMatchesFreshComputation() {
        Integer stale = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    (SELECT * FROM item_search_view EXCEPT SELECT * FROM item_search_rows(NULL))
                    UNION ALL
                    (SELECT * FROM item_search_rows(NULL) EXCEPT SELECT * FROM item_search_view)
                ) differences""", Integer.class);
        assertThat(stale).as("rows in item_search_view that differ from a fresh computation").isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void deletingAFolderUpdatesTheItemsFolderList() throws Exception {
        String token = signUp("a@example.com");
        long cs = createFolder(token, "CS 101");
        long math = createFolder(token, "Math");
        long id = uploadOk(token, "lab1.py", "x".getBytes(StandardCharsets.UTF_8), cs, math);

        // The folder link is removed by ON DELETE CASCADE in the database, not by application code.
        call(delete("/folders/" + math), token);

        assertThat(folderIdsInView(id)).containsExactly(cs);
        assertThat(JsonPath.<List<Object>>read(body(getAs("/items?folderId=" + math, token)), "$")).isEmpty();
    }

    @Test
    void changesMadeOutsideTheAppAreStillPickedUp() throws Exception {
        String token = signUp("a@example.com");
        long cs = createFolder(token, "CS 101");
        long id = uploadOk(token, "lab1.py", "x".getBytes(StandardCharsets.UTF_8), cs);

        // Database triggers do the refreshing, so even a direct SQL change is reflected.
        jdbc.update("UPDATE items SET unique_key = 'renamed by hand' WHERE id = ?", id);

        List<String> found = JsonPath.read(body(getAs("/search?tags=python", token)), "$[*].uniqueKey");
        assertThat(found).containsExactly("renamed by hand");
    }

    @Test
    void aFailedUploadLeavesNoTraceInSearch() throws Exception {
        String token = signUp("a@example.com");
        String other = signUp("b@example.com");
        long othersFolder = createFolder(other, "Not yours");

        upload(token, "lab1.py", "x".getBytes(StandardCharsets.UTF_8), othersFolder); // rejected, rolled back

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_search_view", Integer.class)).isZero();
    }

    private List<Long> folderIdsInView(long itemId) {
        Long[] ids = jdbc.queryForObject("SELECT folder_ids FROM item_search_view WHERE id = ?",
                (rs, n) -> (Long[]) rs.getArray(1).getArray(), itemId);
        return List.of(ids);
    }
}
