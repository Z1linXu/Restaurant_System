package com.restaurant.system.printing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AlloProductionPrintingPolicyTest {
    private final MockEnvironment environment = new MockEnvironment();
    private final PrintingRuntimePolicyProperties runtime = new PrintingRuntimePolicyProperties();
    private final StoreRepository stores = mock(StoreRepository.class);
    private final AlloProductionPrintingPolicy policy = new AlloProductionPrintingPolicy(environment, runtime, stores);
    private final Store allo = new Store();

    @BeforeEach void setUp() {
        environment.setActiveProfiles("allo-test");
        environment.setProperty("app.environment", "production");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_allo_nouilles_test");
        environment.setProperty("spring.datasource.username", "allo_test");
        environment.setProperty("app.delivery.allo-production-printing-enabled", "true");
        environment.setProperty("app.delivery.allo-bootstrap-enabled", "false");
        environment.setProperty("app.template.test-bootstrap.enabled", "false");
        for (String flag : List.of("runtime-enabled", "force-overwrite", "safe-metadata-enabled", "default-users-enabled",
                "demo-data-enabled", "membership-supplement-enabled", "production-bootstrap-enabled")) {
            environment.setProperty("app.seed." + flag, "false");
        }
        runtime.setAllowedModes(List.of("DISABLED", "MOCK", "PAD_DIRECT"));
        runtime.setEndpointConfigurationEnabled(true);
        allo.id = 1L; allo.code = "ALLO_NOUILLES_TEST"; allo.printing_mode = "PAD_DIRECT";
    }

    @Test void controlledAlloCanUsePadAndConfigureEndpoint() {
        when(stores.findById(1L)).thenReturn(Optional.of(allo));
        assertTrue(policy.allowsPadDirect(allo)); assertTrue(policy.padDirectActive(1L));
        assertDoesNotThrow(() -> policy.requireStoreMode(allo, "PAD_DIRECT"));
        assertDoesNotThrow(() -> policy.requireEndpointStore(1L, "192.0.2.10"));
        assertThrows(BusinessException.class, () -> policy.requireStoreMode(allo, "REAL"));
    }

    @Test void otherTemplateStoresRemainInertForPadAndEndpoints() {
        allo.code = "OTHER_TEMPLATE";
        when(stores.findById(1L)).thenReturn(Optional.of(allo));
        assertFalse(policy.allowsPadDirect(allo)); assertEquals("DISABLED", policy.effectiveMode(allo));
        assertThrows(BusinessException.class, () -> policy.requireStoreMode(allo, "PAD_DIRECT"));
        assertThrows(BusinessException.class, () -> policy.requireEndpointStore(1L, "192.0.2.10"));
        allo.printing_mode = "MOCK"; assertEquals("MOCK", policy.effectiveMode(allo));
    }

    @Test void disablingStoreModeOrRuntimeStopsNewWork() {
        when(stores.findById(1L)).thenReturn(Optional.of(allo));
        allo.printing_mode = "MOCK"; assertFalse(policy.padDirectActive(1L));
        allo.printing_mode = "PAD_DIRECT"; environment.setProperty("app.delivery.allo-production-printing-enabled", "false");
        assertFalse(policy.padDirectActive(1L));
    }

    @Test void foreignDatabaseOrRealPolicyCannotAuthorizePhysicalArtifacts() {
        environment.setProperty("spring.datasource.username", "foreign"); assertFalse(policy.allowsPadDirect(allo));
        environment.setProperty("spring.datasource.username", "allo_test");
        runtime.setAllowedModes(List.of("DISABLED", "MOCK", "PAD_DIRECT", "REAL")); assertFalse(policy.allowsPadDirect(allo));
    }

    @Test void onlyTheExistingExactLoopbackQaDatabaseIsAlsoTrusted() {
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:25442/restaurant_allo_nouilles_test");
        assertTrue(policy.allowsPadDirect(allo));
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:25443/restaurant_allo_nouilles_test");
        assertFalse(policy.allowsPadDirect(allo));
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:25442/restaurant_pos");
        assertFalse(policy.allowsPadDirect(allo));
    }

    @Test void ordinaryCloudPadPolicyRetainsExistingBehavior() {
        environment.setActiveProfiles("cloud"); environment.setProperty("app.delivery.allo-production-printing-enabled", "false");
        allo.code = "LEGACY_STORE"; when(stores.findById(1L)).thenReturn(Optional.of(allo));
        assertTrue(policy.padDirectActive(1L)); assertFalse(policy.allowsPadDirect(allo));
    }

    @Test void newQaLoopbackRequiresTheExactAdditionalProfileAndKeepsProductionDatabaseSeparate() {
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test");
        assertFalse(policy.allowsPadDirect(allo));
        environment.setActiveProfiles("allo-test", "allo-qa"); assertTrue(policy.allowsPadDirect(allo));
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:26513/restaurant_allo_nouilles_test");
        assertFalse(policy.allowsPadDirect(allo));
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://db:5432/restaurant_allo_nouilles_test");
        assertFalse(policy.allowsPadDirect(allo));
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:26512/restaurant_allo_nouilles_test");
        environment.setProperty("app.delivery.allo-bootstrap-enabled", "true"); assertFalse(policy.allowsPadDirect(allo));
    }
}
