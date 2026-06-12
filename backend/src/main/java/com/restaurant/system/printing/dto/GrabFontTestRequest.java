package com.restaurant.system.printing.dto;

import jakarta.validation.constraints.NotNull;

public class GrabFontTestRequest {

    @NotNull
    public Long store_id;

    @NotNull
    public Long printer_id;
}
