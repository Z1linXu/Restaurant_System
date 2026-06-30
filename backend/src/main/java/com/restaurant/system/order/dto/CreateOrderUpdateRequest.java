package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderUpdateRequest {

    @NotBlank
    public String idempotency_key;

    @Valid
    @NotEmpty
    public List<CreateOrderItemRequest> items = new ArrayList<>();
}
