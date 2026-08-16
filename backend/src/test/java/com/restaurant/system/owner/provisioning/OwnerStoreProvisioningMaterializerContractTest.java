package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OwnerStoreProvisioningMaterializerContractTest {

    @Test
    void materializerCreatesNonActiveStoreOwnedRowsFromProfileAndMasterOnly() throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java"),
            StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(source)
            .contains("validation_fixture")
            .contains("ready_for_review")
            .contains("phase_b_owner_provisioning")
            .contains("printing_mode = \"mock\"")
            .contains("store_profile")
            .contains("printing_display_rules")
            .contains("master")
            .doesNotContain("source_store_id")
            .doesNotContain("chinatown")
            .doesNotContain("sainte-catherine")
            .doesNotContain("password")
            .doesNotContain("credential")
            .doesNotContain("printer_endpoint")
            .doesNotContain("ip_address");
    }
}
