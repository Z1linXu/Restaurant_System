package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import java.util.List;

public class PrintCenterOverviewResponse {

    public Long store_id;
    public Boolean printing_enabled;
    public List<PrinterConfig> printers;
    public List<PrinterAssignment> assignments;
}
