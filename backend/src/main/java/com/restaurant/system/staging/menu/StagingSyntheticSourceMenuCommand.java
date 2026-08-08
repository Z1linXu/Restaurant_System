package com.restaurant.system.staging.menu;

import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapExecutionContext;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("staging-synthetic-bootstrap")
@ConditionalOnProperty(
    prefix = "stg005.source-menu",
    name = "command-enabled",
    havingValue = "true"
)
public class StagingSyntheticSourceMenuCommand implements ApplicationRunner {

    private static final Set<String> VALUE_OPTIONS = Set.of(
        "source-store-id",
        "source-store-code",
        "expected-runtime-sha",
        "observed-runtime-sha",
        "tool-sha",
        "compose-project",
        "staging-root",
        "printing-mode"
    );
    private static final String EXECUTE = "execute";

    private final Environment environment;
    private final StagingSyntheticSourceMenuGuard guard;
    private final StagingSyntheticSourceMenuService service;
    private final StagingSyntheticSourceMenuEvidenceWriter evidenceWriter;

    public StagingSyntheticSourceMenuCommand(
        Environment environment,
        StagingSyntheticSourceMenuGuard guard,
        StagingSyntheticSourceMenuService service,
        StagingSyntheticSourceMenuEvidenceWriter evidenceWriter
    ) {
        this.environment = environment;
        this.guard = guard;
        this.service = service;
        this.evidenceWriter = evidenceWriter;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        rejectUnknownArguments(arguments);
        StagingSyntheticSourceMenuSpec spec = new StagingSyntheticSourceMenuSpec(
            sourceStoreId(arguments),
            value(arguments, "source-store-code"),
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

        if (!arguments.containsOption(EXECUTE)) {
            evidenceWriter.planned(service.plan(spec));
            return;
        }
        evidenceWriter.completed(service.apply(spec));
    }

    private void rejectUnknownArguments(ApplicationArguments arguments) {
        if (!arguments.getNonOptionArgs().isEmpty()) {
            throw invalidArguments();
        }
        long executeCount = Arrays.stream(arguments.getSourceArgs())
            .filter(argument -> ("--" + EXECUTE).equals(argument))
            .count();
        if (executeCount > 1) {
            throw invalidArguments();
        }
        Set<String> optionNames = arguments.getOptionNames();
        if (!VALUE_OPTIONS.containsAll(optionNames.stream().filter(name -> !EXECUTE.equals(name)).toList())
            || optionNames.stream().anyMatch(name -> !VALUE_OPTIONS.contains(name) && !EXECUTE.equals(name))) {
            throw invalidArguments();
        }
        List<String> executeValues = arguments.getOptionValues(EXECUTE);
        if (executeValues != null && !executeValues.isEmpty()) {
            throw invalidArguments();
        }
    }

    private Long sourceStoreId(ApplicationArguments arguments) {
        try {
            return Long.valueOf(value(arguments, "source-store-id"));
        } catch (NumberFormatException exception) {
            throw invalidArguments();
        }
    }

    private String value(ApplicationArguments arguments, String option) {
        List<String> values = arguments.getOptionValues(option);
        if (values == null || values.size() != 1) {
            throw invalidArguments();
        }
        return values.get(0);
    }

    private StagingSyntheticSourceMenuException invalidArguments() {
        return new StagingSyntheticSourceMenuException(
            "STG005_SOURCE_MENU_ARGUMENTS_INVALID",
            "Synthetic source-menu command arguments are missing, duplicated, or unsupported"
        );
    }
}
