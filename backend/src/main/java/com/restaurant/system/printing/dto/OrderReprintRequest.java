package com.restaurant.system.printing.dto;

public class OrderReprintRequest extends ManualReprintRequest {

    public String receipt_type;
    public Long printer_id;
    public Boolean update_ticket;
}
