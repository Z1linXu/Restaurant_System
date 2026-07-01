package com.restaurant.system.printing.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StoreDeviceEntityNamingTest {

    @Test
    void storeDeviceUsesCamelCaseJavaPropertiesForDerivedJpaQueries() {
        var fieldNames = Arrays.stream(StoreDevice.class.getDeclaredFields())
            .map(field -> field.getName())
            .toList();

        assertThat(fieldNames)
            .contains("organizationId", "storeId", "deviceName", "deviceType", "deviceTokenHash", "lastSeenAt", "appVersion", "isActive", "createdAt", "updatedAt")
            .doesNotContain("organization_id", "store_id", "device_name", "device_type", "device_token_hash", "last_seen_at", "app_version", "is_active", "created_at", "updated_at");
    }
}
