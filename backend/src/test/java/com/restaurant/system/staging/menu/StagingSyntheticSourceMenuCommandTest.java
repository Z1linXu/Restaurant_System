package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.staging.bootstrap.StagingSyntheticBootstrapGuard;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class StagingSyntheticSourceMenuCommandTest {

    private static final String RUNTIME_SHA = "4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c";
    private static final String TOOL_SHA = "1111111111111111111111111111111111111111";
    @Mock
    private StagingSyntheticSourceMenuService service;
    @Mock
    private StagingSyntheticSourceMenuEvidenceWriter evidenceWriter;
    private StagingSyntheticSourceMenuCommand command;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("app.features.printing", "false")
            .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_pos_staging")
            .withProperty("spring.datasource.username", "restaurant_pos_staging")
            .withProperty("spring.main.web-application-type", "none");
        environment.setActiveProfiles("cloud", "staging-synthetic-bootstrap");
        command = new StagingSyntheticSourceMenuCommand(
            environment,
            new StagingSyntheticSourceMenuGuard(new StagingSyntheticBootstrapGuard()),
            service,
            evidenceWriter
        );
    }

    @Test
    void defaultModePlansWithoutApplying() {
        StagingSyntheticSourceMenuResult result = result(false);
        when(service.plan(any())).thenReturn(result);

        command.run(arguments());

        verify(service).plan(any());
        verify(service, never()).apply(any());
        verify(evidenceWriter).planned(result);
    }

    @Test
    void executeModeUsesExplicitFlag() {
        StagingSyntheticSourceMenuResult result = result(false);
        when(service.apply(any())).thenReturn(result);

        command.run(arguments("--execute"));

        verify(service).apply(any());
        verify(service, never()).plan(any());
        verify(evidenceWriter).completed(result);
    }

    @Test
    void unknownOrValuedFlagsAreRejectedBeforeServiceCall() {
        assertThatThrownBy(() -> command.run(arguments("--unknown=value")))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo("STG005_SOURCE_MENU_ARGUMENTS_INVALID")
            );
        assertThatThrownBy(() -> command.run(arguments("--execute=true")))
            .isInstanceOf(StagingSyntheticSourceMenuException.class);
        assertThatThrownBy(() -> command.run(arguments("--execute", "--execute")))
            .isInstanceOf(StagingSyntheticSourceMenuException.class);
        verify(service, never()).plan(any());
        verify(service, never()).apply(any());
    }

    private StagingSyntheticSourceMenuResult result(boolean replayed) {
        return new StagingSyntheticSourceMenuResult(
            91L,
            1L,
            RUNTIME_SHA,
            TOOL_SHA,
            "STG005_SYNTHETIC_ST_DENIS_SOURCE",
            "1",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            1L,
            replayed ? 1L : 2L,
            4,
            3,
            13,
            38,
            "STG005_SOURCE_MENU_READY",
            replayed
        );
    }

    private DefaultApplicationArguments arguments(String... extra) {
        String[] base = {
            "--source-store-id=1",
            "--source-store-code=STG005_SRC_R01",
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
