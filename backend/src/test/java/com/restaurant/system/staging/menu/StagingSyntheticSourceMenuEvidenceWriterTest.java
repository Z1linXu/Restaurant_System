package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class StagingSyntheticSourceMenuEvidenceWriterTest {

    @Test
    void evidenceContainsOnlyBoundedManifestAndCountFields() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StagingSyntheticSourceMenuEvidenceWriter writer =
            new StagingSyntheticSourceMenuEvidenceWriter(new PrintStream(bytes));

        writer.completed(new StagingSyntheticSourceMenuResult(
            91L,
            1L,
            "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c",
            "1111111111111111111111111111111111111111",
            "STG005_SYNTHETIC_ST_DENIS_SOURCE",
            "1",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            1L,
            2L,
            4,
            3,
            13,
            38,
            "STG005_SOURCE_MENU_READY",
            false
        ));

        assertThat(bytes.toString())
            .contains("status=CREATED")
            .contains("bootstrap_request_id=91")
            .contains("runtime_sha=4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c")
            .contains("tool_sha=1111111111111111111111111111111111111111")
            .contains("categories=4|stations=3|items=13|options=38")
            .doesNotContainIgnoringCase("password")
            .doesNotContainIgnoringCase("token")
            .doesNotContainIgnoringCase("endpoint")
            .doesNotContainIgnoringCase("customer")
            .doesNotContainIgnoringCase("order_id")
            .doesNotContainIgnoringCase("payment");
    }
}
