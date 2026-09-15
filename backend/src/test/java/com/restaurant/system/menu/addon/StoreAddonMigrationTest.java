package com.restaurant.system.menu.addon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class StoreAddonMigrationTest {

    static final String MIGRATION = "db/migration/V28__add_store_addon_catalog.sql";
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void createLegacySchema() {
        dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:addon_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table organizations (id bigint primary key)");
        jdbc.execute("create table stores (id bigint primary key, organization_id bigint)");
        jdbc.execute("create table menu_items (id bigint primary key, store_id bigint)");
        jdbc.execute("""
            create table menu_item_options (id bigint primary key, menu_item_id bigint,
                option_code varchar(255), name_zh varchar(255), name_en varchar(255),
                price_delta numeric(38,2), is_active boolean)
            """);
        jdbc.update("insert into organizations values (1), (2)");
        jdbc.update("insert into stores values (10, 1), (11, 1), (20, 2)");
        jdbc.update("insert into menu_items values (100, 10), (110, 11), (200, 20)");
    }

    @Test
    void upgradeAddsNullableLinkWithoutRewritingLegacyRows() throws Exception {
        jdbc.update("insert into menu_item_options values (1000, 100, 'old INVALID code', '旧名', 'Old', 4.25, false)");
        var before = jdbc.queryForMap("select id, option_code, name_zh, name_en, price_delta, is_active from menu_item_options");
        migratePortableSchema();
        assertThat(jdbc.queryForMap("select id, option_code, name_zh, name_en, price_delta, is_active from menu_item_options"))
            .isEqualTo(before);
        assertThat(jdbc.queryForMap("select store_addon_id, store_addon_store_id, addon_eligible from menu_item_options"))
            .containsEntry("STORE_ADDON_ID", null)
            .containsEntry("STORE_ADDON_STORE_ID", null)
            .containsEntry("ADDON_ELIGIBLE", null);
        assertThat(jdbc.queryForObject("select count(*) from store_addons", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from organization_addon_definitions", Integer.class)).isZero();
    }

    @Test
    void rejectsCrossOrganizationAndCrossStoreLinksIncludingParentMoves() throws Exception {
        migratePortableSchema();
        definition(1, 1, "fried_egg");
        definition(2, 2, "fried_egg");
        addon(1, 10, 1, 1);
        assertThatThrownBy(() -> addon(2, 20, 2, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> addon(2, 20, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        option(1000, 100, 1, 10, true);
        assertThatThrownBy(() -> option(1001, 110, 1, 10, true)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> option(1001, 110, 1, 11, true)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update menu_items set store_id = 11 where id = 100"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update stores set organization_id = 2 where id = 10"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update menu_item_options set store_addon_store_id = null where id = 1000"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesSemanticUniquenessButRetainsAllLinkedOptionIdsAndIndependentEligibility() throws Exception {
        migratePortableSchema();
        definition(1, 1, "fried_egg");
        definition(2, 1, "combo_fried_egg");
        definition(3, 2, "fried_egg");
        assertThatThrownBy(() -> definition(4, 1, "fried_egg")).isInstanceOf(DataIntegrityViolationException.class);
        for (String malformed : new String[] {"", "Fried_egg", "fried__egg", "fried_egg_", "fried-egg", " egg"}) {
            assertThatThrownBy(() -> definition(4, 1, malformed)).isInstanceOf(DataIntegrityViolationException.class);
        }
        addon(1, 10, 1, 1);
        addon(2, 11, 1, 1);
        jdbc.update("update store_addons set name_en = null where id = 2");
        assertThat(jdbc.queryForObject("select name_en from store_addons where id = 2", String.class)).isNull();
        assertThatThrownBy(() -> addon(3, 10, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        option(1000, 100, 1, 10, true);
        option(1001, 100, 1, 10, false);
        jdbc.update("update store_addons set active = false where id = 1");
        assertThat(jdbc.queryForList("select id from menu_item_options order by id", Long.class)).containsExactly(1000L, 1001L);
        assertThat(jdbc.queryForList("select addon_eligible from menu_item_options order by id", Boolean.class))
            .containsExactly(true, false);
        assertThatThrownBy(() -> jdbc.update("update menu_item_options set addon_eligible = null where id = 1000"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update store_addons set price = -1 where id = 1"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationIsAdditiveAndDeclaresDatabaseIdentityGuard() throws Exception {
        assertThat(sql().toLowerCase())
            .contains("new.code is distinct from old.code")
            .contains("new.organization_id is distinct from old.organization_id")
            .contains("organization_addon_definition_code_immutable")
            .doesNotContain("update public.order", "insert into public.menu_item_options", "delete from", "drop table", "truncate");
    }

    private void migratePortableSchema() throws Exception {
        // H2 exercises the exact portable DDL. PostgreSQL integration separately
        // executes the entire Flyway migration, including the immutable trigger.
        String portable = sql().substring(0, sql().indexOf("CREATE FUNCTION"));
        new ResourceDatabasePopulator(new ByteArrayResource(portable.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
    }

    private static String sql() throws Exception {
        return new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
    }

    private void definition(long id, long organization, String code) {
        jdbc.update("insert into organization_addon_definitions values (?, ?, ?, current_timestamp, current_timestamp)", id, organization, code);
    }

    private void addon(long id, long store, long organization, long definition) {
        jdbc.update("insert into store_addons values (?, ?, ?, ?, '蛋', 'Egg', 2.50, true, current_timestamp, current_timestamp)", id, store, organization, definition);
    }

    private void option(long id, long item, long addon, long store, boolean eligible) {
        jdbc.update("""
            insert into menu_item_options (id, menu_item_id, store_addon_id, store_addon_store_id, addon_eligible)
            values (?, ?, ?, ?, ?)
            """, id, item, addon, store, eligible);
    }
}
