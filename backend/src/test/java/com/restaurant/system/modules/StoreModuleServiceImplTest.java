package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.modules.dto.StoreModuleUpdateRequest;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreModuleServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreModuleRepository storeModuleRepository;
    @Mock
    private StoreModuleCapabilityProvider capabilityProvider;

    private StoreModuleServiceImpl service;
    private ModuleCatalogDefinition catalog;
    private List<StoreModule> modules;

    @BeforeEach
    void setUp() {
        ModuleContractLoader loader = new ModuleContractLoader();
        catalog = loader.loadCatalog();
        service = new StoreModuleServiceImpl(
            storeRepository,
            storeModuleRepository,
            capabilityProvider,
            loader
        );
        modules = new ArrayList<>();
        modules.addAll(defaultModulesForStore(10L));
        modules.addAll(defaultModulesForStore(20L));
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store(10L, "ACTIVE", "MOCK", true)));
        when(storeRepository.findById(20L)).thenReturn(Optional.of(store(20L, "ACTIVE", "MOCK", true)));
        when(storeModuleRepository.findAllByStoreIdOrderByIdAsc(any())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            return modules.stream()
                .filter(module -> storeId.equals(module.store_id))
                .sorted(Comparator.comparing(module -> module.id))
                .toList();
        });
        when(storeModuleRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<StoreModule> saved = invocation.getArgument(0);
            List<StoreModule> savedList = new ArrayList<>();
            for (StoreModule module : saved) {
                modules.removeIf(existing -> existing.id.equals(module.id));
                modules.add(module);
                savedList.add(module);
            }
            return savedList;
        });
        when(capabilityProvider.environmentCapabilities(any())).thenReturn(normalEnvironmentCapabilities());
        when(capabilityProvider.hardwareCapabilities(any())).thenReturn(normalHardwareCapabilities());
    }

    @Test
    void migratedDefaultStoreConfigurationIsValidWithKdsOff() {
        var response = service.getConfiguration(10L);

        assertTrue(response.valid, () -> "issues: " + response.validation_issues);
        assertEquals(catalog.catalogVersion(), response.catalog_version);
        assertTrue(enabled(response, "PRINTING"));
        assertFalse(enabled(response, "KDS"));
        assertTrue(activeNormalStoreRequired(response, "PRINTING"));
        assertFalse(activeNormalStoreRequired(response, "KDS"));
        assertEquals("A3_FOUNDATION_ONLY_LEGACY_RUNTIME_GATING_RETAINED_UNTIL_A6_A7", response.legacy_compatibility_status);
    }

    @Test
    void productionConstructorIsExplicitlyAutowiredForSpringRuntime() throws NoSuchMethodException {
        Constructor<StoreModuleServiceImpl> constructor = StoreModuleServiceImpl.class.getConstructor(
            StoreRepository.class,
            StoreModuleRepository.class,
            StoreModuleCapabilityProvider.class
        );

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void unknownModuleUpdateFailsClosed() {
        StoreModuleUpdateRequest request = update(toggle("NOT_A_MODULE", true));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateConfiguration(10L, request)
        );

        assertEquals("STORE_MODULE_UNKNOWN: NOT_A_MODULE", exception.getMessage());
    }

    @Test
    void coreModuleCannotBeDisabledForActiveStore() {
        StoreModuleUpdateRequest request = update(toggle("MENU", false));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateConfiguration(10L, request)
        );

        assertEquals("CORE_MODULE_CANNOT_BE_DISABLED: MENU", exception.getMessage());
    }

    @Test
    void optionalModuleCanChangeForOneStoreWithoutChangingAnotherStore() {
        StoreModuleUpdateRequest request = update(toggle("KDS", true));
        when(capabilityProvider.environmentCapabilities(10L)).thenReturn(withKdsEnvironmentCapabilities());
        when(capabilityProvider.hardwareCapabilities(10L)).thenReturn(withKdsHardwareCapabilities());

        var response = service.updateConfiguration(10L, request);

        assertTrue(response.valid, () -> "issues: " + response.validation_issues);
        assertTrue(module(10L, "KDS").enabled);
        assertFalse(module(20L, "KDS").enabled);
        assertEquals("ADMIN_OVERRIDE", module(10L, "KDS").source);
    }

    @Test
    void enablingOptionalModuleWithoutRuntimeCapabilityFailsClosed() {
        StoreModuleUpdateRequest request = update(toggle("KDS", true));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateConfiguration(10L, request)
        );

        assertTrue(exception.getMessage().contains("STORE_MODULE_CONFIGURATION_INVALID"));
        assertTrue(exception.getMessage().contains("ENVIRONMENT_CAPABILITY_MISSING"));
    }

    @Test
    void incompletePersistedConfigurationIsReportedInvalidOnRead() {
        modules.removeIf(module -> Long.valueOf(10L).equals(module.store_id) && "REPORTING_CORE".equals(module.module_key));

        var response = service.getConfiguration(10L);

        assertFalse(response.valid);
        assertTrue(response.validation_issues.stream()
            .anyMatch(issue -> "STORE_MODULE_MISSING".equals(issue.code) && "REPORTING_CORE".equals(issue.module_key)));
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

    private Store store(Long id, String status, String printingMode, Boolean printingEnabled) {
        Store store = new Store();
        store.id = id;
        store.organization_id = 100L;
        store.status = status;
        store.printing_mode = printingMode;
        store.printing_enabled = printingEnabled;
        return store;
    }

    private StoreModule module(Long storeId, String moduleKey) {
        return modules.stream()
            .filter(module -> storeId.equals(module.store_id))
            .filter(module -> moduleKey.equals(module.module_key))
            .findFirst()
            .orElseThrow();
    }

    private boolean enabled(com.restaurant.system.modules.dto.StoreModuleConfigurationResponse response, String moduleKey) {
        return response.modules.stream()
            .filter(module -> moduleKey.equals(module.module_key))
            .findFirst()
            .map(module -> Boolean.TRUE.equals(module.enabled))
            .orElse(false);
    }

    private boolean activeNormalStoreRequired(
        com.restaurant.system.modules.dto.StoreModuleConfigurationResponse response,
        String moduleKey
    ) {
        return response.modules.stream()
            .filter(module -> moduleKey.equals(module.module_key))
            .findFirst()
            .map(module -> Boolean.TRUE.equals(module.active_normal_store_required))
            .orElse(false);
    }

    private StoreModuleUpdateRequest update(StoreModuleUpdateRequest.ModuleUpdate... updates) {
        StoreModuleUpdateRequest request = new StoreModuleUpdateRequest();
        request.store_id = 10L;
        request.modules = List.of(updates);
        return request;
    }

    private StoreModuleUpdateRequest.ModuleUpdate toggle(String moduleKey, boolean enabled) {
        StoreModuleUpdateRequest.ModuleUpdate update = new StoreModuleUpdateRequest.ModuleUpdate();
        update.module_key = moduleKey;
        update.enabled = enabled;
        return update;
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

    private Set<String> normalHardwareCapabilities() {
        return Set.of(
            "TOUCH_CLIENT",
            "PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT",
            "PAD_DEVICE_FOR_PAD_DIRECT"
        );
    }

    private Set<String> withKdsHardwareCapabilities() {
        return Set.of(
            "TOUCH_CLIENT",
            "PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT",
            "PAD_DEVICE_FOR_PAD_DIRECT",
            "KDS_DISPLAY_CLIENT"
        );
    }
}
