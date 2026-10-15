package com.restaurant.system.printing.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.dto.PrinterAssignmentUpdateRequest;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PrinterAssignmentIsolationTest {
    @Test void assignmentCannotBindForeignPrinterEvenWithAnOwnStoreRequestBody() {
        var assignments = mock(PrinterAssignmentRepository.class); var printers = mock(PrinterConfigRepository.class);
        var printer = new PrinterConfig(); printer.id = 900L; printer.store_id = 202L;
        when(printers.findById(printer.id)).thenReturn(Optional.of(printer));
        var request = new PrinterAssignmentUpdateRequest(); request.store_id = 101L;
        request.printer_id = printer.id; request.module_code = "GRAB";
        assertThatThrownBy(() -> new PrinterAssignmentServiceImpl(assignments, printers).saveAssignment(request))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(assignments);
    }
}
