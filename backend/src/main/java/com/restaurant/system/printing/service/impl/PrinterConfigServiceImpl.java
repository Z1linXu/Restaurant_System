package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
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

    public PrinterConfigServiceImpl(
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
        StoreRepository storeRepository
    ) {
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.storeRepository = storeRepository;
    }

    @Override
    public PrintCenterOverviewResponse getOverview(Long storeId) {
        PrintCenterOverviewResponse response = new PrintCenterOverviewResponse();
        response.store_id = storeId;
        response.printing_enabled = isPrintingEnabled(storeId);
        response.printers = getPrinters(storeId);
        response.assignments = printerAssignmentRepository.findAllByStoreIdOrderByIdAsc(storeId);
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
        target.enabled = printerConfig.enabled == null ? true : printerConfig.enabled;
        target.paper_width_mm = printerConfig.paper_width_mm == null ? 80 : printerConfig.paper_width_mm;
        target.timeout_ms = printerConfig.timeout_ms == null ? 3000 : printerConfig.timeout_ms;
        stamp(target, printerConfig.id == null);
        return printerConfigRepository.save(target);
    }

    @Override
    @Transactional
    public PrinterConfig disablePrinter(Long id, Long storeId) {
        PrinterConfig printer = printerConfigRepository.findById(id).orElseThrow(() -> new BusinessException("Printer not found"));
        if (!storeId.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }
        printer.enabled = false;
        printer.updated_at = LocalDateTime.now();
        return printerConfigRepository.save(printer);
    }

    @Override
    public boolean isPrintingEnabled(Long storeId) {
        Store store = requireStore(storeId);
        return store.printing_enabled == null || Boolean.TRUE.equals(store.printing_enabled);
    }

    @Override
    @Transactional
    public boolean updateStorePrintingEnabled(Long storeId, boolean enabled) {
        Store store = requireStore(storeId);
        store.printing_enabled = enabled;
        store.updated_at = LocalDateTime.now();
        storeRepository.save(store);
        return enabled;
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
}
