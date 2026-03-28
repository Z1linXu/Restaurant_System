package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderItemRequest {

    @NotNull
    public Long menu_item_id;

    @NotNull
    @Min(1)
    public Integer quantity;

    public Integer combo_group_no;

    public String combo_role;

    public String notes;

    @Valid
    public List<CreateOrderItemOptionRequest> options = new ArrayList<>();
}
