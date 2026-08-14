package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class StoreProfileMigrationTest {

    @Test
    void v14AddsDatabaseBackedVersionedStoreProfileContractOnly() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V14__add_store_profiles.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("create table public.store_profiles")
            .contains("create table public.store_profile_versions")
            .contains("create table public.store_profile_artifacts")
            .contains("profile_code")
            .contains("profile_version")
            .contains("schema_version")
            .contains("content_json")
            .contains("fingerprint_sha256")
            .contains("prevent_published_store_profile_version_rewrite")
            .contains("prevent_published_store_profile_artifact_rewrite")
            .contains("reject_store_profile_artifact_insert_for_immutable_version")
            .contains("reject_store_profile_artifact_delete_for_immutable_version")
            .contains("store_profile_version_immutable")
            .contains("store_profile_artifact_immutable")
            .contains("new.status not in ('published', 'reviewed', 'ready', 'retired')")
            .contains("new.profile_id is distinct from old.profile_id")
            .contains("new.profile_version_id is distinct from old.profile_version_id")
            .contains("before insert on public.store_profile_artifacts")
            .contains("before delete on public.store_profile_artifacts")
            .contains("module_defaults")
            .contains("menu_template")
            .contains("pricing_policy")
            .contains("combo_configuration")
            .contains("logical_printing_topology")
            .doesNotContain("drop table")
            .doesNotContain("delete from")
            .doesNotContain("truncate")
            .doesNotContain("update public.orders")
            .doesNotContain("update public.order_items")
            .doesNotContain("update public.print_jobs")
            .doesNotContain("update public.users")
            .doesNotContain("password_hash")
            .doesNotContain("access_token")
            .doesNotContain("printer_endpoint")
            .doesNotContain("ip_address");
    }

    @Test
    void profileFingerprintEntitiesMatchPostgresChar64Columns() throws NoSuchFieldException {
        assertFingerprintColumnMatchesChar64(StoreProfileVersionEntity.class);
        assertFingerprintColumnMatchesChar64(StoreProfileArtifactEntity.class);
    }

    private void assertFingerprintColumnMatchesChar64(Class<?> entityType) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField("fingerprint_sha256");
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo("fingerprint_sha256");
        assertThat(column.columnDefinition()).isEqualTo("char(64)");
        assertThat(column.length()).isEqualTo(64);
    }
}
