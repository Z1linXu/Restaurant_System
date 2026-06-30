package com.restaurant.system.printing.dto;

public class PrinterAssignmentUpdateRequest {

    public Long store_id;
    public Long printer_id;
    public String module_code;
    public Boolean enabled;
    public String font_size;
    public Integer takeout_receipt_copies;
}
