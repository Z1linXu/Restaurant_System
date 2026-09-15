package com.restaurant.system.menu.addon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Runs only against an explicitly supplied disposable local PostgreSQL cluster. */
@EnabledIfEnvironmentVariable(named = "ADDON_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class StoreAddonPostgresMigrationTest {

    @Test
    void cleanDatabaseMigratesThroughV28AndSecondStartupIsIdempotent() throws Exception {
        inFreshDatabase(dataSource -> {
            Flyway flyway = flyway(dataSource, null);
            flyway.migrate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '28' and success", Integer.class)).isOne();
            assertThat(jdbc.queryForObject("select count(*) from store_addons", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from organization_addon_definitions", Integer.class)).isZero();
        });
    }

    @Test
    void v26UpgradePreservesLegacyAndSnapshotsAndEnforcesCatalogIdentityAndScope() throws Exception {
        inFreshDatabase(dataSource -> {
            flyway(dataSource, "26").migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            long org = id(jdbc, "insert into organizations (name, code) values ('Test', 'addon_schema') returning id");
            long otherOrg = id(jdbc, "insert into organizations (name, code) values ('Other', 'addon_schema_other') returning id");
            long store = id(jdbc, "insert into stores (name, code, organization_id) values ('Test', 'addon_schema', ?) returning id", org);
            long otherStore = id(jdbc, "insert into stores (name, code, organization_id) values ('Other', 'addon_schema_other', ?) returning id", otherOrg);
            long item = id(jdbc, "insert into menu_items (store_id, name_zh, sort_order) values (?, '测试', 0) returning id", store);
            long otherItem = id(jdbc, "insert into menu_items (store_id, name_zh, sort_order) values (?, '其他', 0) returning id", otherStore);
            long option = id(jdbc, """
                insert into menu_item_options (menu_item_id, option_code, option_group, name_zh, name_en, price_delta, is_active)
                values (?, 'fried_egg', 'ADD_ON', '煎蛋', 'Fried egg', 1.75, true) returning id
                """, item);
            long snapshot = id(jdbc, """
                insert into order_item_options (option_id, option_code_snapshot, option_group_snapshot,
                    option_name_snapshot_zh, option_name_snapshot_en, price_delta, quantity)
                values (?, 'historical_egg', 'ADD_ON', '历史名', 'Historical egg', 1.25, 2) returning id
                """, option);
            var legacyBefore = jdbc.queryForMap("select id, option_code, name_zh, name_en, price_delta, is_active from menu_item_options where id = ?", option);
            var snapshotBefore = jdbc.queryForMap("select * from order_item_options where id = ?", snapshot);

            flyway(dataSource, null).migrate();
            assertThat(jdbc.queryForMap("select id, option_code, name_zh, name_en, price_delta, is_active from menu_item_options where id = ?", option))
                .isEqualTo(legacyBefore);
            assertThat(jdbc.queryForObject("select store_addon_id from menu_item_options where id = ?", Long.class, option)).isNull();
            long definition = id(jdbc, """
                insert into organization_addon_definitions (organization_id, code, created_at, updated_at)
                values (?, 'fried_egg', current_timestamp, current_timestamp) returning id
                """, org);
            long addon = id(jdbc, """
                insert into store_addons (store_id, organization_id, organization_addon_definition_id,
                    name_zh, name_en, price, active, created_at, updated_at)
                values (?, ?, ?, '煎蛋', 'Fried egg', 1.75, true, current_timestamp, current_timestamp) returning id
                """, store, org, definition);
            jdbc.update("update menu_item_options set store_addon_id = ?, store_addon_store_id = ?, addon_eligible = true where id = ?", addon, store, option);
            assertThatThrownBy(() -> jdbc.update("update organization_addon_definitions set code = 'combo_fried_egg' where id = ?", definition))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ORGANIZATION_ADDON_DEFINITION_CODE_IMMUTABLE");
            assertThatThrownBy(() -> jdbc.update("update organization_addon_definitions set organization_id = ? where id = ?", otherOrg, definition))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ORGANIZATION_ADDON_DEFINITION_CODE_IMMUTABLE");
            assertThatThrownBy(() -> jdbc.update("update store_addons set organization_id = ? where id = ?", otherOrg, addon))
                .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("update menu_item_options set menu_item_id = ? where id = ?", otherItem, option))
                .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("update stores set organization_id = ? where id = ?", otherOrg, store))
                .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("update menu_items set store_id = ? where id = ?", otherStore, item))
                .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForMap("select * from order_item_options where id = ?", snapshot)).isEqualTo(snapshotBefore);
            assertThat(flyway(dataSource, null).migrate().migrationsExecuted).isZero();
        });
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static long id(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static void inFreshDatabase(DatabaseTest test) throws Exception {
        String adminUrl = System.getenv("ADDON_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("ADDON_TEST_POSTGRES_USER", System.getProperty("user.name"));
        String password = System.getenv().getOrDefault("ADDON_TEST_POSTGRES_PASSWORD", "");
        String database = "addon_schema_" + UUID.randomUUID().toString().replace("-", "");
        // Names are generated here; only this test's newly created database is removed.
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password)) {
            try (var statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE " + database);
            }
            try {
                String url = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + database;
                test.run(new DriverManagerDataSource(url, user, password));
            } finally {
                try (var statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE " + database);
                }
            }
        }
    }

    @FunctionalInterface
    private interface DatabaseTest {
        void run(DriverManagerDataSource dataSource) throws Exception;
    }
}
