package com.restaurant.system.staging.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class StagingSyntheticBootstrapCommandTest {

    private static final String RUNTIME_SHA = "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c";
    private static final String TOOL_SHA = "1111111111111111111111111111111111111111";
    @Mock
    private StagingSyntheticBootstrapService bootstrapService;
    @Mock
    private StagingSyntheticBootstrapSecretReader secretReader;
    @Mock
    private StagingSyntheticBootstrapEvidenceWriter evidenceWriter;

    private StagingSyntheticBootstrapCommand command;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.features.printing", "false")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_pos_staging")
            .withProperty("spring.datasource.username", "restaurant_pos_staging")
            .withProperty("spring.main.web-application-type", "none");
        environment.setActiveProfiles("cloud", "staging-synthetic-bootstrap");
        command = new StagingSyntheticBootstrapCommand(
            environment,
            new StagingSyntheticBootstrapGuard(),
            bootstrapService,
            secretReader,
            evidenceWriter
        );
    }

    @Test
    void defaultModeValidatesWithoutReadingPasswordOrWritingData() {
        command.run(arguments());

        verify(evidenceWriter).validated(any(StagingSyntheticBootstrapSpec.class));
        verify(secretReader, never()).readPassword();
        verify(bootstrapService, never()).bootstrap(any(), any());
    }

    @Test
    void executeRequiresPasswordStdinFlag() {
        assertThatThrownBy(() -> command.run(arguments("--execute")))
            .isInstanceOfSatisfying(
                StagingSyntheticBootstrapException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_BOOTSTRAP_EXECUTION_GATE_REJECTED")
            );
        verify(bootstrapService, never()).bootstrap(any(), any());
    }

    @Test
    void executeReadsPasswordFromStdinAndClearsMutableBuffer() {
        String rawPassword = syntheticPassword();
        char[] password = rawPassword.toCharArray();
        when(secretReader.readPassword()).thenReturn(password);
        StagingSyntheticBootstrapResult result = new StagingSyntheticBootstrapResult(
            1L,
            "STG005_20260730_R01",
            2L,
            3L,
            4L,
            RUNTIME_SHA,
            TOOL_SHA,
            "STG005_SYNTHETIC_BOOTSTRAP_READY",
            false
        );
        when(bootstrapService.bootstrap(any(), eq(rawPassword))).thenReturn(result);

        command.run(arguments("--execute", "--password-stdin"));

        verify(bootstrapService).bootstrap(any(), eq(rawPassword));
        verify(evidenceWriter).completed(result);
        assertThat(password).containsOnly('\0');
    }

    @Test
    void passwordNeverAppearsInSanitizedEvidenceOutput() {
        String rawPassword = syntheticPassword();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        StagingSyntheticBootstrapEvidenceWriter writer =
            new StagingSyntheticBootstrapEvidenceWriter(new java.io.PrintStream(bytes));
        writer.completed(new StagingSyntheticBootstrapResult(
            1L,
            "STG005_20260730_R01",
            2L,
            3L,
            4L,
            RUNTIME_SHA,
            TOOL_SHA,
            "STG005_SYNTHETIC_BOOTSTRAP_READY",
            false
        ));

        assertThat(bytes.toString())
            .contains("status=CREATED")
            .doesNotContain(rawPassword)
            .doesNotContain("token")
            .doesNotContain("password");
    }

    private String syntheticPassword() {
        return "STG005-" + UUID.randomUUID();
    }

    private DefaultApplicationArguments arguments(String... extra) {
        String[] base = {
            "--run-id=STG005_20260730_R01",
            "--organization-name=STG005_ORG_20260730_R01",
            "--organization-code=STG005_ORG_20260730_R01",
            "--source-store-name=STG005_SRC_20260730_R01",
            "--source-store-code=STG005_SRC_20260730_R01",
            "--owner-login=STG005_OWNER_20260730_R01",
            "--owner-name=STG005_OWNER_20260730_R01",
            "--expected-runtime-sha=" + RUNTIME_SHA,
            "--observed-runtime-sha=" + RUNTIME_SHA,
            "--tool-sha=" + TOOL_SHA,
            "--compose-project=restaurant-pos-staging",
            "--staging-root=/srv/restaurant-pos/staging",
            "--printing-mode=DISABLED"
        };
        String[] combined = Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, combined, base.length, extra.length);
        return new DefaultApplicationArguments(combined);
    }
}
