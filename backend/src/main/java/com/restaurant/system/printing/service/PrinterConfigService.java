package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PrintCenterOverviewResponse;
import com.restaurant.system.printing.entity.PrinterConfig;
import java.util.List;

public interface PrinterConfigService {

    PrintCenterOverviewResponse getOverview(Long storeId);

    List<PrinterConfig> getPrinters(Long storeId);

    PrinterConfig savePrinter(PrinterConfig printerConfig);

    void deletePrinter(Long id, Long storeId);

    boolean isPrintingEnabled(Long storeId);

    String getStorePrintingMode(Long storeId);

    boolean isMockPrinting(Long storeId);

    boolean updateStorePrintingEnabled(Long storeId, boolean enabled);

    String updateStorePrintingMode(Long storeId, String printingMode);
}
