package com.tsukinotes.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares the three ways search has worked, on the same 100,000 items:
 *
 *   1. plain view          (V5-V12)  every search re-aggregates every item's tags
 *   2. materialized view   (V13)     stored rows + GIN index, but every save rebuilds all of them
 *   3. incremental table   (V15)     stored rows + GIN index, and a save updates only its own row
 *
 * Skipped in normal test runs. Run it with (Docker must be running):
 *
 *   ./mvnw test -Dtest=SearchBenchmarkTest -Dbenchmark=true
 *
 * Results are printed and saved to target/search-benchmark.txt. Search times are PostgreSQL's own
 * "Execution Time" from EXPLAIN ANALYZE (median of 30 runs after 5 warm-ups). Save times are the
 * wall-clock time of one transaction that adds an item with 3 tags and a folder (median of 9 runs
 * after 2 warm-ups), including what each approach must do to keep search up to date.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class SearchBenchmarkTest extends IntegrationTest {

    private static final int ITEMS = 100_000;
    private static final int TAGS = 50;
    private static final int TAGS_PER_ITEM = 3;
    private static final int WARMUP = 5;
    private static final int RUNS = 30;
    private static final int SAVE_WARMUP = 2;
    private static final int SAVE_RUNS = 9;

    /** The incremental table's triggers; turned off while measuring the other two approaches. */
    private static final List<String> SYNC_TRIGGERS = List.of(
            "items items_sync_search", "item_tags item_tags_sync_search", "item_folders item_folders_sync_search");

    @Autowired
    private TransactionTemplate transactions;

    private int saveCounter;

    @Test
    void searchAndSaveOn100kItems() throws IOException {
        long identityId = seed();
        // The search row definition as a plain view (what V5-V12 used)...
        jdbc.execute("CREATE VIEW item_search_plain_view AS SELECT * FROM item_search_rows(NULL)");
        // ...and as a materialized view with V13's indexes.
        jdbc.execute("CREATE MATERIALIZED VIEW item_search_matview AS SELECT * FROM item_search_plain_view");
        jdbc.execute("CREATE UNIQUE INDEX item_search_matview_id_idx ON item_search_matview (id)");
        jdbc.execute("CREATE INDEX item_search_matview_tags_idx ON item_search_matview USING GIN (tags)");
        // Put every table in the state autovacuum keeps it in on a real server. Without this, the
        // freshly bulk-filled table is unfairly slow: its rows sit in the GIN index's pending list
        // (unsorted, scanned one by one) and aren't marked all-visible yet.
        jdbc.execute("VACUUM ANALYZE");

        // The same query the app runs (ItemSearchViewRepository.searchByTags), with the source swapped in.
        String tagSearch = "SELECT * FROM %s WHERE identity_id = " + identityId
                + " AND tags @> CAST(ARRAY['tag7','tag23'] AS varchar[]) ORDER BY created_at DESC";
        Result plain = measure(tagSearch.formatted("item_search_plain_view"));
        Result materialized = measure(tagSearch.formatted("item_search_matview"));
        Result incremental = measure(tagSearch.formatted("item_search_view"));

        setSyncTriggers(false);
        double plainSave = medianSaveMillis(identityId, null);
        double materializedSave = medianSaveMillis(identityId, "REFRESH MATERIALIZED VIEW CONCURRENTLY item_search_matview");
        setSyncTriggers(true);
        double incrementalSave = medianSaveMillis(identityId, null);

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "Search benchmark: %,d items, %d tags, %d tags per item%n%n", ITEMS, TAGS, TAGS_PER_ITEM));
        report.append(String.format(Locale.ROOT, "%-44s %12s %18s %18s%n", "", "plain view", "materialized view", "incremental table"));
        report.append(String.format(Locale.ROOT, "%-44s %9.2f ms %15.2f ms %15.2f ms%n",
                "Multi-tag AND search (tag7 AND tag23)", plain.medianMs, materialized.medianMs, incremental.medianMs));
        report.append(String.format(Locale.ROOT, "%-44s %9.2f ms %15.2f ms %15.2f ms%n",
                "Saving an item (with 3 tags and a folder)", plainSave, materializedSave, incrementalSave));
        report.append(String.format(Locale.ROOT, "%nSearch: %.0fx faster than the plain view (%d matching rows).%n",
                plain.medianMs / incremental.medianMs, incremental.rows));
        report.append(String.format(Locale.ROOT, "Saving: %.0fx faster than the materialized view.%n", materializedSave / incrementalSave));
        report.append(String.format(Locale.ROOT, "%nIncremental table plan for the tag search:%n%s%n", incremental.plan));
        report.append(String.format(Locale.ROOT, "Plain view plan for the tag search:%n%s%n", plain.plan));

        System.out.println(report);
        Files.writeString(Path.of("target", "search-benchmark.txt"), report);

        assertThat(incremental.rows).isEqualTo(materialized.rows).isEqualTo(plain.rows).isPositive();
        assertThat(incremental.plan).contains("item_search_view_tags_idx");
        assertThat(incremental.medianMs).isLessThan(plain.medianMs);
        assertThat(incrementalSave).isLessThan(materializedSave);
    }

    /** One user owning all the items (the worst case for a single search), spread over 10 folders. */
    private long seed() {
        jdbc.update("INSERT INTO identities (name, email, password_hash) VALUES ('bench', 'bench@example.com', 'x')");
        long identityId = jdbc.queryForObject("SELECT id FROM identities", Long.class);
        jdbc.update("INSERT INTO tags (name) SELECT 'tag' || n FROM generate_series(1, ?) n", TAGS);
        jdbc.update("INSERT INTO folders (identity_id, name) SELECT ?, 'Folder ' || n FROM generate_series(1, 10) n", identityId);
        // Seeding row by row through the sync triggers would be slow; fill it in one go afterwards.
        setSyncTriggers(false);
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
        jdbc.update("INSERT INTO item_search_view SELECT * FROM item_search_rows(NULL)");
        setSyncTriggers(true);
        return identityId;
    }

    private void setSyncTriggers(boolean enabled) {
        for (String tableAndTrigger : SYNC_TRIGGERS) {
            String[] parts = tableAndTrigger.split(" ");
            jdbc.execute("ALTER TABLE " + parts[0] + (enabled ? " ENABLE" : " DISABLE") + " TRIGGER " + parts[1]);
        }
    }

    /** Times a transaction that adds an item with 3 tags and a folder, then runs afterSave (if any). */
    private double medianSaveMillis(long identityId, String afterSave) {
        List<Double> times = new ArrayList<>();
        for (int i = 0; i < SAVE_WARMUP + SAVE_RUNS; i++) {
            String title = "saved " + (++saveCounter);
            long start = System.nanoTime();
            transactions.executeWithoutResult(status -> {
                Long itemId = jdbc.queryForObject("""
                        INSERT INTO items (identity_id, unique_key, type, content, created_at)
                        VALUES (?, ?, 'note', 'x', now()) RETURNING id""", Long.class, identityId, title);
                jdbc.update("INSERT INTO item_tags (item_id, tag_id) SELECT ?, id FROM tags WHERE name IN ('tag1', 'tag2', 'tag3')", itemId);
                jdbc.update("INSERT INTO item_folders (item_id, folder_id) SELECT ?, id FROM folders WHERE name = 'Folder 1'", itemId);
                if (afterSave != null) {
                    jdbc.execute(afterSave);
                }
            });
            if (i >= SAVE_WARMUP) {
                times.add((System.nanoTime() - start) / 1_000_000.0);
            }
        }
        return median(times);
    }

    private record Result(double medianMs, int rows, String plan) {
    }

    private Result measure(String sql) {
        List<Double> times = new ArrayList<>();
        for (int i = 0; i < WARMUP + RUNS; i++) {
            String json = jdbc.queryForObject("EXPLAIN (ANALYZE, FORMAT JSON) " + sql, String.class);
            if (i >= WARMUP) {
                times.add(((Number) JsonPath.read(json, "$[0]['Execution Time']")).doubleValue());
            }
        }
        int rows = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") q", Integer.class);
        String plan = String.join("\n", jdbc.queryForList("EXPLAIN " + sql, String.class));
        return new Result(median(times), rows, plan);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }
}
