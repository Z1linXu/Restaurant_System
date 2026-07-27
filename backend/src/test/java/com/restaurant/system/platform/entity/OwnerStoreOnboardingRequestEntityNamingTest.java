package com.restaurant.system.platform.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OwnerStoreOnboardingRequestEntityNamingTest {

    @Test
    void onboardingRequestUsesSafeCamelCasePropertiesForJpaQueries() {
        var fieldNames = Arrays.stream(OwnerStoreOnboardingRequest.class.getDeclaredFields())
            .map(field -> field.getName())
            .toList();

        assertThat(fieldNames)
            .contains(
                "organizationId",
                "idempotencyKey",
                "requestFingerprint",
                "status",
                "storeId",
                "resultCode",
                "errorCode",
                "createdAt",
                "updatedAt",
                "completedAt"
            )
            .doesNotContain(
                "rawRequest",
                "password",
                "deviceToken",
                "printerEndpoint"
            );
    }

    @Test
    void migrationUsesOrganizationScopedIdempotencyAndStoresOnlySafeMetadata() throws IOException {
        String migration = new ClassPathResource(
            "db/migration/V8__add_owner_store_onboarding_requests.sql"
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(migration)
            .contains("unique (organization_id, idempotency_key)")
            .contains("request_fingerprint")
            .contains("result_code")
            .contains("error_code")
            .doesNotContain("raw_request")
            .doesNotContain("password")
            .doesNotContain("token")
            .doesNotContain("printer_endpoint");
    }
}
