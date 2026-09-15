package com.restaurant.system.menu.combo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MenuItemComboEggDefaultMigrationTest {
    @Test
    void v27PreservesExistingItemsAndSnapshotsAndLeavesDefaultsInherited() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:combo-v27-upgrade;MODE=PostgreSQL", "sa", "")) {
            connection.createStatement().execute("CREATE TABLE menu_items(id BIGINT PRIMARY KEY, name_zh VARCHAR(100))");
            connection.createStatement().execute("INSERT INTO menu_items VALUES (1, '牛肉面')");
            connection.createStatement().execute("CREATE TABLE order_item_options(id BIGINT PRIMARY KEY, option_code_snapshot VARCHAR(100))");
            connection.createStatement().execute("INSERT INTO order_item_options VALUES (9, 'combo_fried_egg')");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V27__add_item_combo_egg_default.sql"));

            try (var result = connection.createStatement().executeQuery("SELECT name_zh, default_combo_egg_component_code FROM menu_items WHERE id=1")) {
                assertTrue(result.next());
                assertEquals("牛肉面", result.getString(1));
                assertNull(result.getString(2));
            }
            try (var result = connection.createStatement().executeQuery("SELECT option_code_snapshot FROM order_item_options WHERE id=9")) {
                assertTrue(result.next());
                assertEquals("combo_fried_egg", result.getString(1));
            }
        }
    }

    @Test
    void v27AllowsNewItemsToInheritWithoutBackfill() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:combo-v27-clean;MODE=PostgreSQL", "sa", "")) {
            connection.createStatement().execute("CREATE TABLE menu_items(id BIGINT PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V27__add_item_combo_egg_default.sql"));
            connection.createStatement().execute("INSERT INTO menu_items(id) VALUES (1)");
            try (var result = connection.createStatement().executeQuery("SELECT default_combo_egg_component_code FROM menu_items")) {
                assertTrue(result.next());
                assertNull(result.getString(1));
            }
        }
    }
}
