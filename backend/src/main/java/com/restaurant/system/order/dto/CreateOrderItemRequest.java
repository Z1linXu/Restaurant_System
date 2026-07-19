package com.restaurant.system.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderItemRequest {

    @NotNull
    public Long menu_item_id;

    public String item_name_snapshot_zh;

    public String item_name_snapshot_en;

    public BigDecimal unit_price_snapshot;

    public String category_code_snapshot;

    public Long station_id_snapshot;

    public String item_sku_snapshot;

    public String item_type_snapshot;

    @NotNull
    @Min(1)
    public Integer quantity;

    public Integer combo_group_no;

    public String combo_role;

    public String notes;

    @Valid
    public List<CreateOrderItemOptionRequest> options = new ArrayList<>();
}
