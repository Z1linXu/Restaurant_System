package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.printing.dto.PrintCenterOverviewResponse;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.printing.transport.EscPosFontSizeMode;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrinterConfigServiceImpl implements PrinterConfigService {

    private final PrinterConfigRepository printerConfigRepository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final StoreRepository storeRepository;
    private final CloudPrintingGuard cloudPrintingGuard;

    public PrinterConfigServiceImpl(
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
        StoreRepository storeRepository,
        CloudPrintingGuard cloudPrintingGuard
    ) {
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.storeRepository = storeRepository;
        this.cloudPrintingGuard = cloudPrintingGuard;
    }

    @Override
    public PrintCenterOverviewResponse getOverview(Long storeId) {
        PrintCenterOverviewResponse response = new PrintCenterOverviewResponse();
        response.store_id = storeId;
        response.printing_enabled = isPrintingEnabled(storeId);
        response.printing_mode = getStorePrintingMode(storeId);
        response.printers = getPrinters(storeId);
        response.assignments = printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(storeId);
        response.cloud_private_printer_guard_active = cloudPrintingGuard.isStrictCloudProfile();
        response.cloud_private_printer_warning = buildCloudPrivatePrinterWarning(response.printing_mode, response.printers);
        return response;
    }

    @Override
    public List<PrinterConfig> getPrinters(Long storeId) {
        return printerConfigRepository.findAllByStoreIdOrderByIdAsc(storeId);
    }

    @Override
    @Transactional
    public PrinterConfig savePrinter(PrinterConfig printerConfig) {
        PrinterConfig target = printerConfig.id == null
            ? new PrinterConfig()
            : printerConfigRepository.findById(printerConfig.id).orElseThrow(() -> new BusinessException("Printer not found"));
        target.store_id = printerConfig.store_id;
        target.name = printerConfig.name;
        target.ip_address = printerConfig.ip_address;
        target.port = printerConfig.port == null ? 9100 : printerConfig.port;
        target.printer_type = printerConfig.printer_type == null || printerConfig.printer_type.isBlank()
            ? "ESC_POS_TCP"
            : printerConfig.printer_type;
        target.text_encoding = printerConfig.text_encoding == null || printerConfig.text_encoding.isBlank()
            ? "GBK"
            : printerConfig.text_encoding;
        target.escpos_code_page = printerConfig.escpos_code_page;
        target.font_size = printerConfig.font_size == null || printerConfig.font_size.isBlank()
            ? EscPosFontSizeMode.DEFAULT_CODE
            : EscPosFontSizeMode.fromConfig(printerConfig.font_size).code;
        target.font_size_mode = printerConfig.font_size_mode;
        // Printer availability is an operational assignment concern. Physical printer configs stay enabled.
        target.enabled = true;
        target.paper_width_mm = printerConfig.paper_width_mm == null ? 80 : printerConfig.paper_width_mm;
        target.timeout_ms = printerConfig.timeout_ms == null ? 3000 : printerConfig.timeout_ms;
        stamp(target, printerConfig.id == null);
        return printerConfigRepository.save(target);
    }

    @Override
    @Transactional
    public void deletePrinter(Long id, Long storeId) {
        PrinterConfig printer = printerConfigRepository.findById(id).orElseThrow(() -> new BusinessException("Printer not found"));
        if (!storeId.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }
        long assignmentCount = printerAssignmentRepository.countByStoreIdAndPrinterId(storeId, id);
        if (assignmentCount > 0) {
            throw new BusinessException("Printer is currently assigned to modules. Remove assignments before deleting.");
        }
        printerConfigRepository.delete(printer);
    }

    @Override
    public boolean isPrintingEnabled(Long storeId) {
        return !PrintingMode.DISABLED.equals(getStorePrintingMode(storeId));
    }

    @Override
    public String getStorePrintingMode(Long storeId) {
        Store store = requireStore(storeId);
        if (store.printing_mode != null && !store.printing_mode.isBlank()) {
            return PrintingMode.normalize(store.printing_mode);
        }
        if (Boolean.FALSE.equals(store.printing_enabled)) {
            return PrintingMode.DISABLED;
        }
        return PrintingMode.REAL;
    }

    @Override
    public boolean isMockPrinting(Long storeId) {
        return PrintingMode.MOCK.equals(getStorePrintingMode(storeId));
    }

    @Override
    @Transactional
    public boolean updateStorePrintingEnabled(Long storeId, boolean enabled) {
        Store store = requireStore(storeId);
        store.printing_enabled = enabled;
        store.printing_mode = enabled ? PrintingMode.REAL : PrintingMode.DISABLED;
        store.updated_at = LocalDateTime.now();
        storeRepository.save(store);
        return enabled;
    }

    @Override
    @Transactional
    public String updateStorePrintingMode(Long storeId, String printingMode) {
        Store store = requireStore(storeId);
        String normalizedMode = PrintingMode.normalize(printingMode);
        store.printing_mode = normalizedMode;
        store.printing_enabled = !PrintingMode.DISABLED.equals(normalizedMode);
        store.updated_at = LocalDateTime.now();
        storeRepository.save(store);
        return normalizedMode;
    }

    private Store requireStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new BusinessException("Store not found"));
    }

    private void stamp(PrinterConfig printerConfig, boolean isNew) {
        LocalDateTime now = LocalDateTime.now();
        if (isNew) {
            printerConfig.created_at = now;
        }
        printerConfig.updated_at = now;
    }

    private String buildCloudPrivatePrinterWarning(String printingMode, List<PrinterConfig> printers) {
        if (!cloudPrintingGuard.isStrictCloudProfile() || !PrintingMode.REAL.equals(printingMode)) {
            return null;
        }
        boolean hasBlockedPrinter = printers.stream()
            .anyMatch(printer -> cloudPrintingGuard.blockedBackendTcpMessage(printer).isPresent());
        return hasBlockedPrinter
            ? CloudPrintingGuard.ERROR_MESSAGE
            : null;
    }
}
