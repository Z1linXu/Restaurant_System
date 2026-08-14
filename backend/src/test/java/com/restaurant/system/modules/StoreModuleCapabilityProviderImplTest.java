package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.repository.StoreDeviceRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreModuleCapabilityProviderImplTest {

    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private PrinterConfigRepository printerConfigRepository;
    @Mock
    private PrinterAssignmentRepository printerAssignmentRepository;
    @Mock
    private StoreDeviceRepository storeDeviceRepository;

    private StoreModuleCapabilityProviderImpl provider;

    @BeforeEach
    void setUp() {
        PrintingRuntimePolicyProperties runtimePolicy = new PrintingRuntimePolicyProperties();
        provider = new StoreModuleCapabilityProviderImpl(
            featureFlagService,
            runtimePolicy,
            storeRepository,
            printerConfigRepository,
            printerAssignmentRepository,
            storeDeviceRepository
        );
        when(storeDeviceRepository.findAllByStoreIdOrderByIdAsc(10L)).thenReturn(List.of());
    }

    @Test
    void mockPrintingSatisfiesLogicalPrintCapabilitiesWithoutPhysicalPadDirectClient() {
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store(10L, PrintingMode.MOCK)));
        when(printerConfigRepository.findAllByStoreIdOrderByIdAsc(10L)).thenReturn(List.of(
            printer(1L),
            printer(2L),
            printer(3L)
        ));
        when(printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(10L)).thenReturn(List.of(
            assignment(11L, PrintModuleCode.GRAB, 1L),
            assignment(12L, PrintModuleCode.FRONTDESK_RECEIPT, 2L),
            assignment(13L, PrintModuleCode.HOT_KITCHEN, 3L)
        ));

        var capabilities = provider.hardwareCapabilities(10L);

        assertTrue(capabilities.contains(HardwareCapabilityKeys.PRINT_GRAB));
        assertTrue(capabilities.contains(HardwareCapabilityKeys.PRINT_FRONTDESK_RECEIPT));
        assertTrue(capabilities.contains(HardwareCapabilityKeys.PRINT_HOT_KITCHEN));
        assertTrue(capabilities.contains(HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT));
        assertEquals(
            HardwareReadinessState.NOT_REQUIRED,
            readiness(HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT).readinessState()
        );
    }

    @Test
    void padDirectRequiresActiveStoreScopedPadDevice() {
        when(storeRepository.findById(10L)).thenReturn(Optional.of(store(10L, PrintingMode.PAD_DIRECT)));
        when(printerConfigRepository.findAllByStoreIdOrderByIdAsc(10L)).thenReturn(List.of(printer(1L)));
        when(printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(10L)).thenReturn(List.of(
            assignment(11L, PrintModuleCode.GRAB, 1L)
        ));

        assertFalse(provider.hardwareCapabilities(10L).contains(HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT));
        assertEquals(
            HardwareReadinessState.UNCONFIGURED,
            readiness(HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT).readinessState()
        );

        StoreDevice device = new StoreDevice();
        device.id = 99L;
        device.storeId = 10L;
        device.deviceType = "ANDROID_PAD";
        device.platform = "ANDROID";
        device.status = "ACTIVE";
        device.isActive = true;
        when(storeDeviceRepository.findAllByStoreIdOrderByIdAsc(10L)).thenReturn(List.of(device));

        assertTrue(provider.hardwareCapabilities(10L).contains(HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT));
        assertEquals(
            HardwareReadinessState.CONFIGURED,
            readiness(HardwareCapabilityKeys.PAD_DIRECT_PRINT_CLIENT).readinessState()
        );
    }

    private StoreHardwareCapabilityReadiness readiness(String capabilityKey) {
        return provider.hardwareReadiness(10L).stream()
            .filter(readiness -> capabilityKey.equals(readiness.capabilityKey()))
            .findFirst()
            .orElseThrow();
    }

    private Store store(Long storeId, String printingMode) {
        Store store = new Store();
        store.id = storeId;
        store.printing_mode = printingMode;
        return store;
    }

    private PrinterConfig printer(Long id) {
        PrinterConfig printer = new PrinterConfig();
        printer.id = id;
        printer.store_id = 10L;
        printer.enabled = true;
        return printer;
    }

    private PrinterAssignment assignment(Long id, String moduleCode, Long printerId) {
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.id = id;
        assignment.store_id = 10L;
        assignment.module_code = moduleCode;
        assignment.printer_id = printerId;
        assignment.enabled = true;
        return assignment;
    }
}
