package com.restaurant.system.menu.combo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class StoreComboComponentMigrationTest {

    @Test
    void v12AddsStoreScopedCanonicalComboComponentsWithoutHistoricalRewrite() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V12__add_store_combo_components.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("create table public.store_combo_components")
            .contains("store_id bigint not null")
            .contains("component_group")
            .contains("component_code")
            .contains("unique (store_id, component_group, component_code)")
            .contains("foreign key (store_id) references public.stores(id)")
            .contains("combo_tea_egg")
            .contains("combo_fried_egg")
            .contains("combo_edamame")
            .contains("combo_shredded_potato")
            .contains("combo_cucumber_salad")
            .contains("from public.stores store_row")
            .doesNotContain("drop table")
            .doesNotContain("delete from")
            .doesNotContain("truncate")
            .doesNotContain("update public.order")
            .doesNotContain("password")
            .doesNotContain("token")
            .doesNotContain("credential")
            .doesNotContain("printer_endpoint");
    }
}
