package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OwnerStoreProvisioningMaterializerContractTest {

    @Test
    void materializerCreatesAndValidatesACompleteLiveStoreInOneTransaction() throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java"),
            StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(source)
            .contains("validation_fixture")
            .contains("store_created_live")
            .contains("phase_b_owner_provisioning")
            .contains("printing_mode = \"disabled\"")
            .contains("organizationrepository.findbyidforupdate")
            .contains("baselineprovisioner.provision")
            .contains("evaluateoperationalbaseline")
            .contains("recordautomaticactivation")
            .contains("store.status = \"active\"")
            .contains("store.lifecycle_status = \"active\"")
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

        assertThat(source.indexOf("organizationrepository.findbyidforupdate"))
            .isLessThan(source.indexOf("requireuniquestorecode"));
    }
}
