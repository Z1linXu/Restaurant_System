package com.restaurant.system.owner.master;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PhaseBMasterMenuSeedMigrationTest {

    @Test
    void v19SeedsMasterMenuFromReviewedProfileArtifactsOnly() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V19__seed_phase_b_master_menu_profile_v2.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("st_denis_canonical_profile")
            .contains("menu_template")
            .contains("join public.store_profile_artifacts artifact")
            .contains("chain_master_menus")
            .contains("chain_master_menu_versions")
            .contains("chain_master_menu_categories")
            .contains("chain_master_menu_products")
            .contains("chain_master_menu_options")
            .contains("lanzhou_chain_master_menu")
            .contains("chain_master_menu_v1")
            .contains("e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7")
            .contains("sku_counts")
            .contains("sku_counts.sku_count = 1")
            .contains("|| ':' || (item_rows.item_json ->> 'item_ref')")
            .contains("product_rows.master_product_key || ':'")
            .contains("product_key:option_group:option_code")
            .contains("product_key:option_ref")
            .contains("parent_master_option_key")
            .contains("coalesce(nullif(option_rows.option_json ->> 'sort_order', '')::integer, option_rows.ordinal::integer)")
            .contains("coalesce(nullif(option_entry.value ->> 'sort_order', '')::integer, 0)")
            .contains("printing_display_rules")
            .contains("442214c92fabc31801d5e9aff9e08b97eadd404b91141ad9efe28180fea081a0")
            .contains("profile_version = 'v2'")
            .contains("2083269d602cf068b78551ee5d53916442dc262a77c4fa5eaef8eae5dc1267c2")
            .doesNotContain("from public.stores")
            .doesNotContain("join public.stores")
            .doesNotContain("insert into public.stores")
            .doesNotContain("drop table")
            .doesNotContain("truncate")
            .doesNotContain("delete from")
            .doesNotContain("update public.orders")
            .doesNotContain("update public.order_items")
            .doesNotContain("update public.print_jobs")
            .doesNotContain("update public.users")
            .doesNotContain("password")
            .doesNotContain("token")
            .doesNotContain("credential")
            .doesNotContain("printer_endpoint")
            .doesNotContain("ip_address")
            .doesNotContain("source_store_id");
    }

    @Test
    void catalogConstantsExposeInitialMasterIdentity() {
        assertThat(ChainMasterMenuCatalogService.INITIAL_MASTER_MENU_KEY)
            .isEqualTo("LANZHOU_CHAIN_MASTER_MENU");
        assertThat(ChainMasterMenuCatalogService.INITIAL_MASTER_MENU_VERSION)
            .isEqualTo("v1");
        assertThat(ChainMasterMenuCatalogService.INITIAL_MASTER_MENU_FINGERPRINT)
            .isEqualTo("ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c");
    }

    @Test
    void v21RepairsSeededFingerprintToRuntimeCanonicalAuthority() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V21__repair_phase_b_master_menu_fingerprint.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("phase_b_master_fingerprint_repair_blocked_by_provisioned_store")
            .contains("phase_b_master_fingerprint_repair_blocked_by_completed_request")
            .contains("disable trigger trg_chain_master_menu_versions_immutable")
            .contains("enable trigger trg_chain_master_menu_versions_immutable")
            .contains("disable trigger trg_store_profile_versions_immutable")
            .contains("enable trigger trg_store_profile_versions_immutable")
            .contains("e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7")
            .contains("ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c")
            .contains("2083269d602cf068b78551ee5d53916442dc262a77c4fa5eaef8eae5dc1267c2")
            .contains("51ddf408755ef476ac99abd9ab7498f48995431c5d5a52d98a77704ab71b23ae")
            .contains("jsonb_set")
            .contains("master_menu_reference,fingerprint_sha256")
            .doesNotContain("drop table")
            .doesNotContain("truncate")
            .doesNotContain("delete from");
    }
}
