package com.restaurant.system.platform.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OwnerStoreMenuCloneRequestEntityNamingTest {

    @Test
    void requestEntityContainsOnlyScopedAndSanitizedEvidenceFields() {
        var fieldNames = Arrays.stream(OwnerStoreMenuCloneRequest.class.getDeclaredFields())
            .map(field -> field.getName())
            .toList();

        assertThat(fieldNames)
            .contains(
                "organizationId",
                "sourceStoreId",
                "targetStoreId",
                "idempotencyKey",
                "requestFingerprint",
                "profileCode",
                "status",
                "sourceMenuRevision",
                "targetRevisionBefore",
                "targetRevisionAfter",
                "createdStationCount",
                "createdCategoryCount",
                "createdItemCount",
                "createdOptionCount",
                "resultCode",
                "errorCode",
                "actorUserId",
                "createdAt",
                "updatedAt",
                "completedAt"
            )
            .doesNotContain(
                "rawRequest",
                "requestPayload",
                "menuPayload",
                "password",
                "token",
                "printerEndpoint",
                "errorMessage"
            );
    }

    @Test
    void v10IsAppendOnlyAndDefinesExactScopeConstraintAndTargetIndex() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V10__add_owner_store_menu_clone_requests.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("create table public.owner_store_menu_clone_requests")
            .contains("unique (\n        organization_id,\n        source_store_id,\n        target_store_id,\n        idempotency_key")
            .contains("create index idx_owner_store_menu_clone_target_store")
            .contains("on public.owner_store_menu_clone_requests (target_store_id)")
            .contains("request_fingerprint")
            .contains("result_code")
            .contains("error_code")
            .doesNotContain("alter table")
            .doesNotContain("drop table")
            .doesNotContain("raw_request")
            .doesNotContain("menu_payload")
            .doesNotContain("password")
            .doesNotContain("device_token")
            .doesNotContain("printer_endpoint");
    }
}
