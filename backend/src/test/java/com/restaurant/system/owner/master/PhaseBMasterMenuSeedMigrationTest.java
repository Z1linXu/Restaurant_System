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
            .isEqualTo("e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7");
    }
}
