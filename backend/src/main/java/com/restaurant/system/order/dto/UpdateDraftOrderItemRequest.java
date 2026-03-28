package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class UpdateDraftOrderItemRequest {

    public Integer quantity;
    public Integer combo_group_no;
    public String combo_role;
    public String notes;

    @Valid
    public List<CreateOrderItemOptionRequest> options = new ArrayList<>();
}
