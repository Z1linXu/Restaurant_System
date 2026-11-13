package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderUpdateRequest {

    @NotBlank
    public String idempotency_key;

    /** Missing means preserve; empty string explicitly clears, guarded by expected_revision. */
    @jakarta.validation.constraints.Size(max = 2000)
    public String order_notes;

    public Integer expected_revision;

    @Valid
    public List<CreateOrderItemRequest> items = new ArrayList<>();
}
