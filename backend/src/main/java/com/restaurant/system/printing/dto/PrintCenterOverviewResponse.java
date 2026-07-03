package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import java.util.List;

public class PrintCenterOverviewResponse {

    public Long store_id;
    public Boolean printing_enabled;
    public String printing_mode;
    public Boolean cloud_private_printer_guard_active;
    public String cloud_private_printer_warning;
    public List<PrinterConfig> printers;
    public List<PrinterAssignment> assignments;
}
