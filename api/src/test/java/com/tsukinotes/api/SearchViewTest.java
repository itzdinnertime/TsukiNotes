package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * item_search_view is a materialized view (a stored copy), so these check it never goes stale:
 * every way the underlying data can change must show up in searches straight away.
 */
class SearchViewTest extends IntegrationTest {

    @Test
    void isAMaterializedViewWithAGinIndexOnTags() {
        Integer materialized = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_matviews WHERE matviewname = 'item_search_view'", Integer.class);
        String tagsIndex = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'item_search_view_tags_idx'", String.class);

        assertThat(materialized).isEqualTo(1);
        assertThat(tagsIndex).containsIgnoringCase("USING gin (tags)");
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
