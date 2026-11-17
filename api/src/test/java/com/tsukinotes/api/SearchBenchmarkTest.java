package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures search on 100,000 items: the old plain view vs the materialized view with a GIN index.
 * Skipped in normal test runs. Run it with (Docker must be running):
 *
 *   ./mvnw test -Dtest=SearchBenchmarkTest -Dbenchmark=true
 *
 * Results are printed and saved to target/search-benchmark.txt. Times are PostgreSQL's own
 * "Execution Time" from EXPLAIN ANALYZE (median of 30 runs after 5 warm-up runs), so they
 * exclude network and Java overhead.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class SearchBenchmarkTest extends IntegrationTest {

    private static final int ITEMS = 100_000;
    private static final int TAGS = 50;
    private static final int TAGS_PER_ITEM = 3;
    private static final int WARMUP = 5;
    private static final int RUNS = 30;

    @Test
    void searchOn100kItems() throws IOException {
        long identityId = seed();
        // The pre-V13 definition, kept side by side so both are measured on identical data.
        jdbc.execute("""
                CREATE VIEW item_search_plain_view AS
                SELECT items.id, items.identity_id, items.unique_key, items.type, items.content, items.created_at,
                       items.stored_file_name IS NOT NULL AS has_file, items.original_file_name,
                       items.content_type, items.size_bytes,
                       array_remove(array_agg(tags.name), NULL) AS tags,
                       COALESCE((SELECT array_agg(item_folders.folder_id::bigint ORDER BY item_folders.folder_id)
                                 FROM item_folders WHERE item_folders.item_id = items.id), '{}'::bigint[]) AS folder_ids
                FROM items
                LEFT JOIN item_tags ON items.id = item_tags.item_id
                LEFT JOIN tags ON item_tags.tag_id = tags.id
                GROUP BY items.id""");
        jdbc.execute("ANALYZE");

        // The same query the app runs (ItemSearchViewRepository.searchByTags), with the view name swapped in.
        String tagSearch = "SELECT * FROM %s WHERE identity_id = " + identityId
                + " AND tags @> CAST(ARRAY['tag7','tag23'] AS varchar[]) ORDER BY created_at DESC";

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "Search benchmark: %,d items, %d tags, %d tags per item%n%n",
                ITEMS, TAGS, TAGS_PER_ITEM));

        Result plainTags = measure(tagSearch.formatted("item_search_plain_view"));
        Result matTags = measure(tagSearch.formatted("item_search_view"));
        report.append(row("Multi-tag AND search (tag7 AND tag23)", plainTags, matTags));

        report.append(String.format(Locale.ROOT, "%nCost on writes (what the speed-up is paid with):%n"));
        report.append(String.format(Locale.ROOT, "  REFRESH MATERIALIZED VIEW CONCURRENTLY: %.0f ms (median of 5)%n",
                medianMillis(5, () -> jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY item_search_view"))));
        report.append(String.format(Locale.ROOT, "  Adding one item (insert + automatic refresh): %.0f ms (median of 5)%n",
                medianMillis(5, () -> jdbc.update("INSERT INTO items (identity_id, unique_key, type, content, created_at) "
                        + "VALUES (?, 'bench-' || gen_random_uuid(), 'note', 'x', now())", identityId))));

        report.append(String.format(Locale.ROOT, "%nMaterialized view plan for the tag search:%n%s%n", matTags.plan));
        report.append(String.format(Locale.ROOT, "Plain view plan for the tag search:%n%s%n", plainTags.plan));

        System.out.println(report);
        Files.writeString(Path.of("target", "search-benchmark.txt"), report);

        assertThat(matTags.rows).isEqualTo(plainTags.rows).isPositive();
        assertThat(matTags.plan).contains("item_search_view_tags_idx");
        assertThat(matTags.medianMs).isLessThan(plainTags.medianMs);
    }

    /** One user owning all the items (the worst case for a single search), spread over 10 folders. */
    private long seed() {
        jdbc.update("INSERT INTO identities (name, email, password_hash) VALUES ('bench', 'bench@example.com', 'x')");
        long identityId = jdbc.queryForObject("SELECT id FROM identities", Long.class);
        jdbc.update("INSERT INTO tags (name) SELECT 'tag' || n FROM generate_series(1, ?) n", TAGS);
        jdbc.update("INSERT INTO folders (identity_id, name) SELECT ?, 'Folder ' || n FROM generate_series(1, 10) n", identityId);
        jdbc.update("""
                INSERT INTO items (identity_id, unique_key, type, content, created_at)
                SELECT ?, 'item ' || n, 'note', 'note text ' || n, TIMESTAMP '2026-01-01' + n * INTERVAL '1 minute'
                FROM generate_series(1, ?) n""", identityId, ITEMS);
        // Each item gets TAGS_PER_ITEM tags picked by a hash, so the data is the same on every run.
        jdbc.update("""
                INSERT INTO item_tags (item_id, tag_id)
                SELECT DISTINCT items.id, tags.id
                FROM items
                CROSS JOIN generate_series(0, ? - 1) k
                JOIN tags ON tags.name = 'tag' || (1 + abs(hashint4(items.id * 31 + k)) % ?)""", TAGS_PER_ITEM, TAGS);
        jdbc.update("""
                INSERT INTO item_folders (item_id, folder_id)
                SELECT items.id, folders.id FROM items
                JOIN folders ON folders.name = 'Folder ' || (1 + items.id % 10)""");
        return identityId;
    }

    private record Result(double medianMs, int rows, String plan) {
    }

    private Result measure(String sql) {
        List<Double> times = new ArrayList<>();
        String json = null;
        for (int i = 0; i < WARMUP + RUNS; i++) {
            json = jdbc.queryForObject("EXPLAIN (ANALYZE, FORMAT JSON) " + sql, String.class);
            if (i >= WARMUP) {
                times.add(((Number) JsonPath.read(json, "$[0]['Execution Time']")).doubleValue());
            }
        }
        int rows = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") q", Integer.class);
        String plan = String.join("\n", jdbc.queryForList("EXPLAIN " + sql, String.class));
        return new Result(median(times), rows, plan);
    }

    private double medianMillis(int runs, Runnable action) {
        List<Double> times = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            action.run();
            times.add((System.nanoTime() - start) / 1_000_000.0);
        }
        return median(times);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    private static String row(String name, Result before, Result after) {
        return String.format(Locale.ROOT, "%-40s plain view %8.2f ms  ->  materialized %6.2f ms  (%.0fx faster, %d rows)%n",
                name, before.medianMs, after.medianMs, before.medianMs / after.medianMs, after.rows);
    }
}
