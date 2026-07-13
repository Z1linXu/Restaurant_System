package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PrinterTestRequest;
import com.restaurant.system.printing.dto.PrinterTestResponse;
import com.restaurant.system.printing.dto.PrinterEncodingTestRequest;
import com.restaurant.system.printing.dto.PrinterEncodingTestResponse;
import com.restaurant.system.printing.dto.GrabFontTestRequest;
import com.restaurant.system.printing.dto.GrabFontTestResponse;
import com.restaurant.system.printing.dto.ModuleAssignmentTestRequest;
import com.restaurant.system.printing.dto.OrderReprintRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.dto.PrinterConnectionTestRequest;
import com.restaurant.system.printing.dto.PrinterConnectionTestResponse;
import com.restaurant.system.printing.dto.OrderPrintOptionResponse;
import java.util.List;

public interface PrintDispatcherService {

    void dispatchAfterCommit(String moduleCode, Long storeId, Long orderId);

    void dispatchOrderUpdateAfterCommit(String moduleCode, Long storeId, Long orderId, Long orderUpdateBatchId);

    void dispatchPersistedEvent(
        String moduleCode,
        Long storeId,
        Long orderId,
        Long orderUpdateBatchId,
        String sourceKey
    );

    boolean hasPrintableContent(String moduleCode, Long storeId, Long orderId);

    boolean hasPrintableUpdateContent(String moduleCode, Long storeId, Long orderId, Long orderUpdateBatchId);

    PrinterTestResponse testPrint(PrinterTestRequest request);

    PrinterEncodingTestResponse testEncodings(PrinterEncodingTestRequest request);

    GrabFontTestResponse testGrabFontModes(GrabFontTestRequest request);

    PrinterTestResponse testCurrentFontSize(PrinterTestRequest request);

    PrinterTestResponse testAssignedModulePrint(ModuleAssignmentTestRequest request);

    PrintJobResponse reprintJob(Long jobId, Long requestedByUserId);

    PrintJobResponse reprintOrder(Long orderId, OrderReprintRequest request, Long requestedByUserId);

    List<OrderPrintOptionResponse> getOrderPrintOptions(Long orderId);

    PrinterConnectionTestResponse testConnection(PrinterConnectionTestRequest request);
}
