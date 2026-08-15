package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PrintingDisplayRuleMigrationTest {

    @Test
    void v17AddsStoreScopedVersionedDisplayRulesWithoutHistoricalRewrite() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V17__add_printing_display_rules.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("create table public.printing_display_rule_sets")
            .contains("store_id bigint not null")
            .contains("unique (store_id)")
            .contains("create table public.printing_display_rule_revisions")
            .contains("rule_set_id bigint not null")
            .contains("revision_number integer not null")
            .contains("content_json text not null")
            .contains("chk_printing_display_rule_revisions_json")
            .contains("fingerprint_sha256 character(64) not null")
            .contains("printing_rule_revision_id bigint")
            .contains("printing_rule_fingerprint character(64)")
            .contains("trg_printing_display_rule_revisions_immutable")
            .contains("from public.stores store_row")
            .contains("printing_display_rules_v1")
            .contains("frontdesk_receipt")
            .contains("hot_kitchen")
            .doesNotContain("drop table")
            .doesNotContain("truncate")
            .doesNotContain("delete from public.orders")
            .doesNotContain("update public.orders")
            .doesNotContain("update public.order_items")
            .doesNotContain("password")
            .doesNotContain("token")
            .doesNotContain("credential")
            .doesNotContain("printer_endpoint");
    }
}
