package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrinterConfigServiceImplTest {

    @Mock
    private PrinterConfigRepository printerConfigRepository;
    @Mock
    private PrinterAssignmentRepository printerAssignmentRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private CloudPrintingGuard cloudPrintingGuard;

    private PrinterConfigServiceImpl service;
    private PrintingRuntimePolicyProperties runtimePolicy;

    @BeforeEach
    void setUp() {
        runtimePolicy = new PrintingRuntimePolicyProperties();
        runtimePolicy.validate();
        service = new PrinterConfigServiceImpl(
            printerConfigRepository,
            printerAssignmentRepository,
            storeRepository,
            cloudPrintingGuard,
            runtimePolicy
        );
    }

    @Test
    void runtimePolicyRejectsRealModeWhenOnlyDisabledAndMockAreAllowed() {
        runtimePolicy.setAllowedModes(java.util.List.of("DISABLED", "MOCK"));
        runtimePolicy.validate();
        com.restaurant.system.user.entity.Store store = new com.restaurant.system.user.entity.Store();
        store.id = 1L;
        store.printing_mode = "DISABLED";
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateStorePrintingMode(1L, "REAL")
        );

        assertEquals("Printing mode REAL is not allowed by the runtime policy", exception.getMessage());
        verify(storeRepository, never()).save(any(com.restaurant.system.user.entity.Store.class));
    }

    @Test
    void runtimePolicyAllowsMockAndPersistsEnabledState() {
        runtimePolicy.setAllowedModes(java.util.List.of("DISABLED", "MOCK"));
        runtimePolicy.validate();
        com.restaurant.system.user.entity.Store store = new com.restaurant.system.user.entity.Store();
        store.id = 1L;
        store.printing_mode = "DISABLED";
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(storeRepository.save(any(com.restaurant.system.user.entity.Store.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("MOCK", service.updateStorePrintingMode(1L, "MOCK"));
        assertEquals("MOCK", store.printing_mode);
        assertEquals(true, store.printing_enabled);
    }

    @Test
    void stagingPadDirectPolicyPersistsPadModeButRejectsRealWithoutAnotherSave() {
        runtimePolicy.setAllowedModes(java.util.List.of("DISABLED", "MOCK", "PAD_DIRECT"));
        runtimePolicy.setEndpointConfigurationEnabled(false);
        runtimePolicy.validate();
        com.restaurant.system.user.entity.Store store = new com.restaurant.system.user.entity.Store();
        store.id = 18L;
        store.printing_mode = "DISABLED";
        when(storeRepository.findById(18L)).thenReturn(Optional.of(store));
        when(storeRepository.save(store)).thenReturn(store);

        assertEquals("PAD_DIRECT", service.updateStorePrintingMode(18L, "PAD_DIRECT"));
        assertEquals("PAD_DIRECT", store.printing_mode);
        assertEquals(true, store.printing_enabled);
        assertThrows(BusinessException.class, () -> service.updateStorePrintingMode(18L, "REAL"));
        assertEquals("PAD_DIRECT", store.printing_mode);
        verify(storeRepository, org.mockito.Mockito.times(1)).save(store);
    }

    @Test
    void blankPersistedModeResolvesToDisabledInsteadOfLegacyRealFallback() {
        com.restaurant.system.user.entity.Store store = new com.restaurant.system.user.entity.Store();
        store.id = 1L;
        store.printing_enabled = true;
        store.printing_mode = " ";
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        assertEquals("DISABLED", service.getStorePrintingMode(1L));
    }

    @Test
    void unknownPersistedModeResolvesToDisabledInsteadOfLegacyRealFallback() {
        com.restaurant.system.user.entity.Store store = new com.restaurant.system.user.entity.Store();
        store.id = 1L;
        store.printing_enabled = true;
        store.printing_mode = "surprise";
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        assertEquals("DISABLED", service.getStorePrintingMode(1L));
    }

    @Test
    void explicitModeMutationRejectsUnknownMode() {
        com.restaurant.system.user.entity.Store store = new com.restaurant.system.user.entity.Store();
        store.id = 1L;
        store.printing_mode = "DISABLED";
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.updateStorePrintingMode(1L, "surprise")
        );

        assertEquals("Unsupported printing mode: SURPRISE", exception.getMessage());
        verify(storeRepository, never()).save(any(com.restaurant.system.user.entity.Store.class));
    }

    @Test
    void runtimePolicyRejectsEndpointConfigurationWhenDisabled() {
        runtimePolicy.setEndpointConfigurationEnabled(false);
        PrinterConfig request = printer(null, 1L, "No endpoint allowed");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.savePrinter(request));

        assertEquals("Printer endpoint configuration is disabled by the runtime policy", exception.getMessage());
        verify(printerConfigRepository, never()).save(any(PrinterConfig.class));
    }

    @Test
    void endpointPermissionAllowsCreateAndUpdateWithoutAllowingRealMode() {
        runtimePolicy.setAllowedModes(java.util.List.of("DISABLED", "MOCK", "PAD_DIRECT"));
        runtimePolicy.setEndpointConfigurationEnabled(true);
        runtimePolicy.validate();
        when(printerConfigRepository.save(any(PrinterConfig.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        PrinterConfig request = printer(null, 18L, "Endpoint policy test");
        request.ip_address = "create.test.invalid";
        request.port = 9100;
        request.timeout_ms = 3100;
        request.paper_width_mm = 80;
        request.text_encoding = "GBK";
        PrinterConfig created = service.savePrinter(request);
        assertEquals("create.test.invalid", created.ip_address);
        assertEquals(9100, created.port);
        assertEquals(3100, created.timeout_ms);

        created.id = 7L;
        when(printerConfigRepository.findByIdAndStoreId(7L, 18L)).thenReturn(Optional.of(created));
        PrinterConfig update = printer(7L, 18L, "Endpoint policy test updated");
        update.ip_address = "update.test.invalid";
        update.port = 9101;
        update.timeout_ms = 4500;
        update.paper_width_mm = 58;
        update.text_encoding = "UTF-8";
        PrinterConfig saved = service.savePrinter(update);
        assertEquals("update.test.invalid", saved.ip_address);
        assertEquals(9101, saved.port);
        assertEquals(4500, saved.timeout_ms);
        assertEquals(58, saved.paper_width_mm);
        assertEquals("UTF-8", saved.text_encoding);
        assertThrows(BusinessException.class, () -> runtimePolicy.requireAllowedMode("REAL"));
        org.mockito.Mockito.verifyNoInteractions(cloudPrintingGuard);
    }

    @Test
    void rejectsMovingExistingPrinterAcrossStores() {
        PrinterConfig existing = printer(7L, 1L, "Existing");
        PrinterConfig request = printer(7L, 2L, "Moved");
        when(printerConfigRepository.findByIdAndStoreId(7L, 2L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.savePrinter(request)
        );

        assertEquals("Printer not found", exception.getMessage());
        assertEquals(1L, existing.store_id);
        assertEquals("Existing", existing.name);
        verify(printerConfigRepository).findByIdAndStoreId(7L, 2L);
        verify(printerConfigRepository, never()).findById(7L);
        verify(printerConfigRepository, never()).save(any(PrinterConfig.class));
    }

    @Test
    void updatesExistingPrinterWithinItsStore() {
        PrinterConfig existing = printer(7L, 1L, "Existing");
        PrinterConfig request = printer(7L, 1L, "Updated");
        request.port = 9200;
        when(printerConfigRepository.findByIdAndStoreId(7L, 1L)).thenReturn(Optional.of(existing));
        when(printerConfigRepository.save(any(PrinterConfig.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PrinterConfig saved = service.savePrinter(request);

        assertEquals(1L, saved.store_id);
        assertEquals("Updated", saved.name);
        assertEquals(9200, saved.port);
        verify(printerConfigRepository).save(existing);
    }

    @Test
    void rejectsPrinterWithoutStoreScope() {
        PrinterConfig request = printer(null, null, "Unscoped");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.savePrinter(request)
        );

        assertEquals("Printer store is required", exception.getMessage());
        verify(printerConfigRepository, never()).save(any(PrinterConfig.class));
    }

    private PrinterConfig printer(Long id, Long storeId, String name) {
        PrinterConfig printer = new PrinterConfig();
        printer.id = id;
        printer.store_id = storeId;
        printer.name = name;
        printer.ip_address = "printer.test.invalid";
        printer.port = 9100;
        return printer;
    }
}
