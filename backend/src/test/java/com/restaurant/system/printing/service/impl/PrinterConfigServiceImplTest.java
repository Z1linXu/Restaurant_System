package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.CloudPrintingGuard;
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

    @BeforeEach
    void setUp() {
        service = new PrinterConfigServiceImpl(
            printerConfigRepository,
            printerAssignmentRepository,
            storeRepository,
            cloudPrintingGuard
        );
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
