package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PrintCenterOverviewResponse;
import com.restaurant.system.printing.entity.PrinterConfig;
import java.util.List;

public interface PrinterConfigService {

    PrintCenterOverviewResponse getOverview(Long storeId);

    List<PrinterConfig> getPrinters(Long storeId);

    PrinterConfig savePrinter(PrinterConfig printerConfig);

    PrinterConfig disablePrinter(Long id, Long storeId);

    boolean isPrintingEnabled(Long storeId);

    boolean updateStorePrintingEnabled(Long storeId, boolean enabled);
}
