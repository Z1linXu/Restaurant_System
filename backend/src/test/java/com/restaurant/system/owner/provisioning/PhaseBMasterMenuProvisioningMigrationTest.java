package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.user.entity.Store;
import jakarta.persistence.Column;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PhaseBMasterMenuProvisioningMigrationTest {

    @Test
    void v18AddsPhaseBMasterMenuAndProvisioningContractsWithoutRuntimeMutation() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V18__add_phase_b_master_menu_provisioning.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        String normalizedMigration = migration.toLowerCase();

        assertThat(migration)
            .contains("CHECK (master_category_key ~ '^[A-Za-z0-9][A-Za-z0-9_:-]*$')")
            .contains("CHECK (master_product_key ~ '^[A-Za-z0-9][A-Za-z0-9_:-]*$')")
            .contains("CHECK (master_option_key ~ '^[A-Za-z0-9][A-Za-z0-9_:-]*$')");

        assertThat(normalizedMigration)
            .contains("add column store_kind")
            .contains("add column lifecycle_status")
            .contains("add column provisioning_source")
            .contains("provisioned_profile_fingerprint_sha256 character(64)")
            .contains("provisioned_master_menu_fingerprint_sha256 character(64)")
            .contains("create table public.chain_master_menus")
            .contains("create table public.chain_master_menu_versions")
            .contains("create table public.chain_master_menu_categories")
            .contains("create table public.chain_master_menu_products")
            .contains("create table public.chain_master_menu_options")
            .contains("create table public.store_menu_master_mappings")
            .contains("create table public.owner_store_provisioning_requests")
            .contains("uq_owner_store_provisioning_organization_key unique")
            .contains("idx_chain_master_menu_products_sku")
            .contains("fingerprint_sha256 character(64) not null")
            .contains("source_profile_fingerprint_sha256 character(64) not null")
            .contains("master_menu_fingerprint_sha256 character(64) not null")
            .contains("origin = 'store_only'")
            .contains("mapping_status = 'store_only'")
            .contains("old.status = 'published' and new.status = 'retired'")
            .contains("chk_store_profile_artifacts_type")
            .contains("'printing_display_rules'")
            .contains("trg_chain_master_menu_versions_immutable")
            .contains("chain_master_menu_version_immutable")
            .contains("business")
            .contains("validation_fixture")
            .contains("phase_b_owner_provisioning")
            .contains("staging_validation")
            .doesNotContain("drop table")
            .doesNotContain("truncate")
            .doesNotContain("delete from")
            .doesNotContain("insert into public.stores")
            .doesNotContain("update public.orders")
            .doesNotContain("update public.order_items")
            .doesNotContain("update public.print_jobs")
            .doesNotContain("update public.users")
            .doesNotContain("password")
            .doesNotContain("token")
            .doesNotContain("credential")
            .doesNotContain("printer_endpoint")
            .doesNotContain("ip_address")
            .doesNotContain("source_store_id")
            .doesNotContain("uq_chain_master_menu_products_sku unique");
    }

    @Test
    void v20AlignsProvisioningValidationVocabularyWithRuntimeContract() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V20__align_phase_b_provisioning_validation_status.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("drop constraint chk_owner_store_provisioning_validation_status")
            .contains("validation_status in ('pending', 'pass', 'warning', 'blocking', 'failed')")
            .contains("pass / warning / blocking")
            .doesNotContain("'passed'")
            .doesNotContain("drop table")
            .doesNotContain("truncate")
            .doesNotContain("delete from")
            .doesNotContain("insert into public.stores");
    }

    @Test
    void phaseBFingerprintEntitiesMatchPostgresChar64Columns() throws NoSuchFieldException {
        assertChar64Column(Store.class, "provisioned_profile_fingerprint_sha256");
        assertChar64Column(Store.class, "provisioned_master_menu_fingerprint_sha256");
        assertChar64Column(ChainMasterMenuVersionEntity.class, "fingerprint_sha256");
        assertChar64Column(ChainMasterMenuVersionEntity.class, "source_profile_fingerprint_sha256");
        assertChar64Column(OwnerStoreProvisioningRequestEntity.class, "request_fingerprint");
        assertChar64Column(OwnerStoreProvisioningRequestEntity.class, "profile_fingerprint_sha256");
        assertChar64Column(OwnerStoreProvisioningRequestEntity.class, "master_menu_fingerprint_sha256");
    }

    private void assertChar64Column(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(fieldName);
        assertThat(column.columnDefinition()).isEqualTo("char(64)");
        assertThat(column.length()).isEqualTo(64);
        assertThat(field.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.CHAR);
    }
}
