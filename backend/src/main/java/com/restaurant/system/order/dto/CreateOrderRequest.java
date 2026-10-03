package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderRequest {

    @NotNull
    public Long store_id;

    public Long created_by;

    @NotBlank
    public String order_type;

    public String table_no;

    public String pickup_no;

    /** Frozen whole-order instructions; accepted only by trusted external-order creation. */
    public String external_order_note_snapshot;

    @Valid
    @NotNull
    public List<CreateOrderItemRequest> items = new ArrayList<>();
}
