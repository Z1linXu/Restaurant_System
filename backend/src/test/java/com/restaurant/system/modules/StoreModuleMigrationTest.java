package com.restaurant.system.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class StoreModuleMigrationTest {

    @Test
    void v13AddsStoreScopedModulePersistenceWithDeterministicDefaultsOnly() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V13__add_store_modules.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("create table public.store_modules")
            .contains("store_id bigint not null")
            .contains("module_key")
            .contains("enabled boolean not null")
            .contains("unique (store_id, module_key)")
            .contains("foreign key (store_id) references public.stores(id)")
            .contains("ordering_pos")
            .contains("menu_management")
            .contains("printing")
            .contains("reporting_core")
            .contains("kds")
            .contains("analytics_advanced")
            .contains("from public.stores store_row")
            .contains("cross join canonical_modules")
            .doesNotContain("drop table")
            .doesNotContain("delete from")
            .doesNotContain("truncate")
            .doesNotContain("update public.orders")
            .doesNotContain("update public.order_items")
            .doesNotContain("update public.print_jobs")
            .doesNotContain("password")
            .doesNotContain("token")
            .doesNotContain("credential")
            .doesNotContain("printer_endpoint");
    }
}
