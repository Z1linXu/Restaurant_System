package com.restaurant.system.staging.menu;

import java.io.PrintStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("staging-synthetic-bootstrap")
public class StagingSyntheticSourceMenuEvidenceWriter {

    private final PrintStream output;

    public StagingSyntheticSourceMenuEvidenceWriter() {
        this(System.out);
    }

    StagingSyntheticSourceMenuEvidenceWriter(PrintStream output) {
        this.output = output;
    }

    public void planned(StagingSyntheticSourceMenuResult result) {
        write("VALIDATED", result);
    }

    public void completed(StagingSyntheticSourceMenuResult result) {
        write(result.replayed() ? "REPLAYED" : "CREATED", result);
    }

    private void write(String status, StagingSyntheticSourceMenuResult result) {
        output.printf(
            "STG005_SOURCE_MENU|status=%s|bootstrap_request_id=%d|source_store_id=%d|runtime_sha=%s|tool_sha=%s|manifest=%s|version=%s|fingerprint=%s|revision_before=%d|revision_after=%d|categories=%d|stations=%d|items=%d|options=%d|result_code=%s%n",
            status,
            result.bootstrapRequestId(),
            result.sourceStoreId(),
            result.runtimeSha(),
            result.toolSha(),
            result.manifestCode(),
            result.manifestVersion(),
            result.manifestFingerprint(),
            result.revisionBefore(),
            result.revisionAfter(),
            result.categoryCount(),
            result.stationCount(),
            result.itemCount(),
            result.optionCount(),
            result.resultCode()
        );
    }
}
