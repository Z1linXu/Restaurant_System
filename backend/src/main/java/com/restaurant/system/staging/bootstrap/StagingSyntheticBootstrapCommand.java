package com.restaurant.system.staging.bootstrap;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile(StagingSyntheticBootstrapGuard.BOOTSTRAP_PROFILE)
@ConditionalOnProperty(
    prefix = "stg005.bootstrap",
    name = "command-enabled",
    havingValue = "true"
)
public class StagingSyntheticBootstrapCommand implements ApplicationRunner {

    private static final Set<String> VALUE_OPTIONS = Set.of(
        "run-id",
        "organization-name",
        "organization-code",
        "source-store-name",
        "source-store-code",
        "owner-login",
        "owner-name",
        "expected-runtime-sha",
        "observed-runtime-sha",
        "tool-sha",
        "compose-project",
        "staging-root",
        "printing-mode"
    );
    private static final Set<String> FLAG_OPTIONS = Set.of("execute", "password-stdin");

    private final Environment environment;
    private final StagingSyntheticBootstrapGuard guard;
    private final StagingSyntheticBootstrapService bootstrapService;
    private final StagingSyntheticBootstrapSecretReader secretReader;
    private final StagingSyntheticBootstrapEvidenceWriter evidenceWriter;

    public StagingSyntheticBootstrapCommand(
        Environment environment,
        StagingSyntheticBootstrapGuard guard,
        StagingSyntheticBootstrapService bootstrapService,
        StagingSyntheticBootstrapSecretReader secretReader,
        StagingSyntheticBootstrapEvidenceWriter evidenceWriter
    ) {
        this.environment = environment;
        this.guard = guard;
        this.bootstrapService = bootstrapService;
        this.secretReader = secretReader;
        this.evidenceWriter = evidenceWriter;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        rejectUnknownArguments(arguments);
        boolean execute = arguments.containsOption("execute");
        boolean passwordStdin = arguments.containsOption("password-stdin");
        if (execute != passwordStdin) {
            throw new StagingSyntheticBootstrapException(
                "STG005_BOOTSTRAP_EXECUTION_GATE_REJECTED",
                "Execute mode requires both --execute and --password-stdin"
            );
        }

        StagingSyntheticBootstrapSpec spec = new StagingSyntheticBootstrapSpec(
            value(arguments, "run-id"),
            value(arguments, "organization-name"),
            value(arguments, "organization-code"),
            value(arguments, "source-store-name"),
            value(arguments, "source-store-code"),
            value(arguments, "owner-login"),
            value(arguments, "owner-name"),
            value(arguments, "expected-runtime-sha"),
            value(arguments, "tool-sha")
        );
        StagingSyntheticBootstrapExecutionContext context =
            new StagingSyntheticBootstrapExecutionContext(
                Set.of(environment.getActiveProfiles()),
                value(arguments, "compose-project"),
                value(arguments, "staging-root"),
                value(arguments, "expected-runtime-sha"),
                value(arguments, "observed-runtime-sha"),
                value(arguments, "tool-sha"),
                value(arguments, "printing-mode"),
                environment.getProperty("app.features.printing", Boolean.class, true),
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.main.web-application-type")
            );
        guard.validate(context, spec);

        if (!execute) {
            evidenceWriter.validated(spec);
            return;
        }

        char[] password = secretReader.readPassword();
        try {
            StagingSyntheticBootstrapResult result = bootstrapService.bootstrap(
                spec,
                new String(password)
            );
            evidenceWriter.completed(result);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void rejectUnknownArguments(ApplicationArguments arguments) {
        if (!arguments.getNonOptionArgs().isEmpty()) {
            throw invalidArguments();
        }
        Set<String> allowed = VALUE_OPTIONS.stream().collect(Collectors.toSet());
        allowed.addAll(FLAG_OPTIONS);
        if (!allowed.containsAll(arguments.getOptionNames())) {
            throw invalidArguments();
        }
        for (String flag : FLAG_OPTIONS) {
            List<String> values = arguments.getOptionValues(flag);
            if (values != null && !values.isEmpty()) {
                throw invalidArguments();
            }
        }
    }

    private String value(ApplicationArguments arguments, String option) {
        List<String> values = arguments.getOptionValues(option);
        if (values == null || values.size() != 1) {
            throw invalidArguments();
        }
        return values.get(0);
    }

    private StagingSyntheticBootstrapException invalidArguments() {
        return new StagingSyntheticBootstrapException(
            "STG005_BOOTSTRAP_ARGUMENTS_INVALID",
            "Bootstrap command arguments are missing, duplicated, or unsupported"
        );
    }
}
