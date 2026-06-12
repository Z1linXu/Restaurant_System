package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PrinterAssignmentUpdateRequest;
import com.restaurant.system.printing.entity.PrinterAssignment;
import java.util.List;

public interface PrinterAssignmentService {

    List<PrinterAssignment> getAssignments(Long storeId);

    PrinterAssignment saveAssignment(PrinterAssignmentUpdateRequest request);
}
