package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrinterAssignmentUpdateRequest;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrinterAssignmentService;
import com.restaurant.system.printing.transport.EscPosFontSizeMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrinterAssignmentServiceImpl implements PrinterAssignmentService {

    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final PrinterConfigRepository printerConfigRepository;

    public PrinterAssignmentServiceImpl(
        PrinterAssignmentRepository printerAssignmentRepository,
        PrinterConfigRepository printerConfigRepository
    ) {
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.printerConfigRepository = printerConfigRepository;
    }

    @Override
    public List<PrinterAssignment> getAssignments(Long storeId) {
        return printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(storeId);
    }

    @Override
    @Transactional
    public PrinterAssignment saveAssignment(PrinterAssignmentUpdateRequest request) {
        if (request.module_code == null || !PrintModuleCode.ALL.contains(request.module_code)) {
            throw new BusinessException("Unsupported module code");
        }
        if (request.printer_id != null) {
            PrinterConfig printer = printerConfigRepository.findById(request.printer_id)
                .orElseThrow(() -> new BusinessException("Assigned printer not found"));
            if (!request.store_id.equals(printer.store_id)) {
                throw new BusinessException("Assigned printer does not belong to store");
            }
        }

        PrinterAssignment target = printerAssignmentRepository.findByStoreIdAndModuleCode(request.store_id, request.module_code)
            .orElseGet(PrinterAssignment::new);
        boolean isNew = target.id == null;
        target.store_id = request.store_id;
        target.printer_id = request.printer_id;
        target.module_code = request.module_code;
        target.enabled = request.enabled == null ? false : request.enabled;
        target.font_size = EscPosFontSizeMode.fromConfig(request.font_size).code;
        LocalDateTime now = LocalDateTime.now();
        if (isNew) {
            target.created_at = now;
        }
        target.updated_at = now;
        return printerAssignmentRepository.save(target);
    }
}
