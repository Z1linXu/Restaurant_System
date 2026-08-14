package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreModuleAccessEvaluatorTest {

    @Mock
    private StoreModuleRepository storeModuleRepository;
    @Mock
    private StoreModuleCapabilityProvider capabilityProvider;

    private StoreModuleAccessEvaluator evaluator;
    private ModuleCatalogDefinition catalog;
    private List<StoreModule> modules;

    @BeforeEach
    void setUp() {
        ModuleContractLoader loader = new ModuleContractLoader();
        catalog = loader.loadCatalog();
        evaluator = new StoreModuleAccessEvaluator(storeModuleRepository, capabilityProvider, loader);
        modules = new ArrayList<>(defaultModulesForStore(10L));
        when(storeModuleRepository.findAllByStoreIdOrderByIdAsc(any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            return modules.stream()
                .filter(module -> storeId.equals(module.store_id))
                .sorted(Comparator.comparing(module -> module.id))
                .toList();
        });
        when(capabilityProvider.environmentCapabilities(any())).thenReturn(normalEnvironmentCapabilities());
    }

    @Test
    void enabledCoreModuleWithEnvironmentCapabilityIsAllowed() {
        StoreModuleAccessEvaluation evaluation = evaluator.evaluateCapability(10L, ModuleKeys.MENU_MANAGEMENT);

        assertTrue(evaluation.allowed());
        assertTrue(evaluation.storeModuleEnabled());
        assertTrue(evaluation.environmentAvailable());
        assertDoesNotThrow(() -> evaluator.requireCapability(10L, ModuleKeys.MENU_MANAGEMENT));
    }

    @Test
    void disabledOptionalModuleReturnsCanonicalModuleDisabled() {
        StoreModuleAccessEvaluation evaluation = evaluator.evaluateCapability(10L, ModuleKeys.KDS);

        assertFalse(evaluation.allowed());
        assertEquals(StoreModuleAccessEvaluator.MODULE_DISABLED, evaluation.errorCode());
        ModuleAccessException exception = assertThrows(
            ModuleAccessException.class,
            () -> evaluator.requireCapability(10L, ModuleKeys.KDS)
        );
        assertEquals(StoreModuleAccessEvaluator.MODULE_DISABLED, exception.getErrorCode());
        assertEquals(ModuleKeys.KDS, exception.getModuleKey());
    }

    @Test
    void enabledModuleWithMissingEnvironmentCapabilityFailsClosed() {
        when(capabilityProvider.environmentCapabilities(10L)).thenReturn(Set.of("DATABASE", "AUTH_RUNTIME"));

        StoreModuleAccessEvaluation evaluation = evaluator.evaluateCapability(10L, ModuleKeys.ORDERING_POS);

        assertFalse(evaluation.allowed());
        assertEquals(StoreModuleAccessEvaluator.MODULE_ENVIRONMENT_CAPABILITY_MISSING, evaluation.errorCode());
        assertTrue(evaluation.missingEnvironmentCapabilities().contains("CORE_POS_RUNTIME"));
        assertTrue(evaluation.missingEnvironmentCapabilities().contains("WEBSOCKET_RUNTIME"));
    }

    @Test
    void requiredDependencyDisabledFailsClosedBeforeBusinessAction() {
        module(ModuleKeys.MENU).enabled = false;

        StoreModuleAccessEvaluation evaluation = evaluator.evaluateCapability(10L, ModuleKeys.ORDERING_POS);

        assertFalse(evaluation.allowed());
        assertEquals(StoreModuleAccessEvaluator.MODULE_CONFIGURATION_INVALID, evaluation.errorCode());
        assertTrue(evaluation.issueCodes().contains("REQUIRED_MODULE_DISABLED:" + ModuleKeys.MENU));
    }

    @Test
    void crossStoreModuleStateIsNotLeakedFromAnotherStore() {
        modules.addAll(defaultModulesForStore(20L));
        module(20L, ModuleKeys.KDS).enabled = true;
        when(capabilityProvider.environmentCapabilities(20L)).thenReturn(withKdsEnvironmentCapabilities());

        assertFalse(evaluator.evaluateCapability(10L, ModuleKeys.KDS).allowed());
        assertTrue(evaluator.evaluateCapability(20L, ModuleKeys.KDS).allowed());
    }

    private List<StoreModule> defaultModulesForStore(Long storeId) {
        AtomicLong ids = new AtomicLong(storeId * 100);
        LocalDateTime now = LocalDateTime.now();
        return catalog.modules().stream()
            .map(definition -> {
                StoreModule module = new StoreModule();
                module.id = ids.incrementAndGet();
                module.store_id = storeId;
                module.module_key = definition.moduleKey();
                module.enabled = definition.defaultState() == ModuleState.ENABLED;
                module.source = "MIGRATION_DEFAULT";
                module.configuration_status = "CONFIGURED";
                module.metadata_json = "{}";
                module.created_at = now;
                module.updated_at = now;
                return module;
            })
            .toList();
    }

    private StoreModule module(String moduleKey) {
        return module(10L, moduleKey);
    }

    private StoreModule module(Long storeId, String moduleKey) {
        return modules.stream()
            .filter(module -> storeId.equals(module.store_id))
            .filter(module -> moduleKey.equals(module.module_key))
            .findFirst()
            .orElseThrow();
    }

    private Set<String> normalEnvironmentCapabilities() {
        return Set.of(
            "CORE_POS_RUNTIME",
            "AUTH_RUNTIME",
            "DATABASE",
            "WEBSOCKET_RUNTIME",
            "ADMIN_RUNTIME",
            "PRINTING_FEATURE_FLAG",
            "PRINT_MODE_RUNTIME",
            "ANALYTICS_FEATURE_FLAG"
        );
    }

    private Set<String> withKdsEnvironmentCapabilities() {
        return Set.of(
            "CORE_POS_RUNTIME",
            "AUTH_RUNTIME",
            "DATABASE",
            "WEBSOCKET_RUNTIME",
            "ADMIN_RUNTIME",
            "PRINTING_FEATURE_FLAG",
            "PRINT_MODE_RUNTIME",
            "ANALYTICS_FEATURE_FLAG",
            "KDS_FEATURE_FLAG"
        );
    }
}
