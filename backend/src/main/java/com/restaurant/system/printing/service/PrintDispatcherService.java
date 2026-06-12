package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PrinterTestRequest;
import com.restaurant.system.printing.dto.PrinterTestResponse;
import com.restaurant.system.printing.dto.PrinterEncodingTestRequest;
import com.restaurant.system.printing.dto.PrinterEncodingTestResponse;
import com.restaurant.system.printing.dto.GrabFontTestRequest;
import com.restaurant.system.printing.dto.GrabFontTestResponse;
import com.restaurant.system.printing.dto.ModuleAssignmentTestRequest;

public interface PrintDispatcherService {

    void dispatchAfterCommit(String moduleCode, Long storeId, Long orderId);

    PrinterTestResponse testPrint(PrinterTestRequest request);

    PrinterEncodingTestResponse testEncodings(PrinterEncodingTestRequest request);

    GrabFontTestResponse testGrabFontModes(GrabFontTestRequest request);

    PrinterTestResponse testCurrentFontSize(PrinterTestRequest request);

    PrinterTestResponse testAssignedModulePrint(ModuleAssignmentTestRequest request);
}
