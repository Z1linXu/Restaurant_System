package com.restaurant.system.printing;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Only newly generated disposable databases on explicitly selected loopback PostgreSQL. */
@EnabledIfEnvironmentVariable(named = "PRINTING_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class PrintingReliabilityPostgresMigrationTest {
    @Test void cleanAndV29UpgradeAreAdditiveSnapshotSafeAndReplayable() throws Exception {
        String adminUrl = System.getenv("PRINTING_TEST_POSTGRES_URL");
        String user = System.getProperty("user.name");
        String database = "print_v30_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(adminUrl, user, "")) {
            try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + database); }
            try {
                var ds = new DriverManagerDataSource(adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + database, user, "");
                Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("29").load().migrate();
                var jdbc = new JdbcTemplate(ds);
                Long job = jdbc.queryForObject("insert into print_jobs (status, rendered_text_snapshot, payload_snapshot, module_code) values ('PRINTED', '历史走洋葱', '{}', 'GRAB') returning id", Long.class);
                Long option = jdbc.queryForObject("insert into order_item_options (option_code_snapshot, option_name_snapshot_zh, price_delta, quantity) values ('remove_onion', '历史洋葱', 0, 1) returning id", Long.class);
                var before = jdbc.queryForMap("select status, rendered_text_snapshot, payload_snapshot, printed_at from print_jobs where id = ?", job);
                var frozen = jdbc.queryForMap("select * from order_item_options where id = ?", option);
                var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
                assertThat(flyway.migrate().migrationsExecuted).isOne();
                assertThat(jdbc.queryForMap("select status, rendered_text_snapshot, payload_snapshot, printed_at from print_jobs where id = ?", job)).isEqualTo(before);
                assertThat(jdbc.queryForMap("select * from order_item_options where id = ?", option)).isEqualTo(frozen);
                assertThat(jdbc.queryForObject("select preferred_device_id from print_jobs where id = ?", Long.class, job)).isNull();
                assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '30' and success", Integer.class)).isOne();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                assertThatThrownBy(() -> jdbc.update("update print_jobs set preferred_device_id = 999999 where id = ?", job)).hasMessageContaining("violates foreign key");
            } finally {
                try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE " + database); }
            }
        }
    }
}
