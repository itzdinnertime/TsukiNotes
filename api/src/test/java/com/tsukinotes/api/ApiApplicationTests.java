package com.tsukinotes.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The app starts, and every database migration applies cleanly to an empty database. */
class ApiApplicationTests extends IntegrationTest {

    @Test
    void contextLoadsAndAllMigrationsApply() {
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(failed).isZero();

        Integer latest = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class);
        assertThat(latest).isGreaterThanOrEqualTo(12);
    }
}
