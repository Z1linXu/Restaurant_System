package com.restaurant.system.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateDraftOrderItemQuantityRequest {

    @NotNull
    @Min(1)
    public Integer quantity;
}
