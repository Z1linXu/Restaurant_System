package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.common.auth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

class OwnerStoreProvisioningFingerprintTest {

    @Test
    void businessAndStagingPurposesCannotReplayTheSameIdempotencyRequest() {
        OwnerStoreProvisioningFingerprint fingerprint = new OwnerStoreProvisioningFingerprint();

        assertThat(fingerprint.fingerprint(command(StoreProvisioningPurpose.BUSINESS)))
            .isNotEqualTo(fingerprint.fingerprint(command(StoreProvisioningPurpose.STAGING_VALIDATION)));
    }

    private OwnerStoreProvisioningCommand command(StoreProvisioningPurpose purpose) {
        return new OwnerStoreProvisioningCommand(
            new AuthenticatedUser(20L, null, 1L, "owner", "Owner", "OWNER"),
            10L,
            "same-key",
            "Business Store",
            "BUSINESS_STORE",
            "ST_DENIS_CANONICAL_PROFILE",
            "v2",
            "p".repeat(64),
            "LANZHOU_CHAIN_MASTER_MENU",
            "v1",
            "m".repeat(64),
            purpose
        );
    }
}
