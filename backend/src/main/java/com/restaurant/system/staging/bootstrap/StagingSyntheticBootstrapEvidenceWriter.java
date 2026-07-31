package com.restaurant.system.staging.bootstrap;

import java.io.PrintStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(StagingSyntheticBootstrapGuard.BOOTSTRAP_PROFILE)
public class StagingSyntheticBootstrapEvidenceWriter {

    private final PrintStream output;

    public StagingSyntheticBootstrapEvidenceWriter() {
        this(System.out);
    }

    StagingSyntheticBootstrapEvidenceWriter(PrintStream output) {
        this.output = output;
    }

    public void validated(StagingSyntheticBootstrapSpec spec) {
        output.printf(
            "STG005_BOOTSTRAP|status=VALIDATED|run_id=%s|runtime_sha=%s|tool_sha=%s%n",
            spec.runId(),
            spec.runtimeSha(),
            spec.toolSha()
        );
    }

    public void completed(StagingSyntheticBootstrapResult result) {
        output.printf(
            "STG005_BOOTSTRAP|status=%s|run_id=%s|organization_id=%d|source_store_id=%d|owner_user_id=%d|runtime_sha=%s|tool_sha=%s%n",
            result.replayed() ? "REPLAYED" : "CREATED",
            result.runId(),
            result.organizationId(),
            result.sourceStoreId(),
            result.ownerUserId(),
            result.runtimeSha(),
            result.toolSha()
        );
    }
}
