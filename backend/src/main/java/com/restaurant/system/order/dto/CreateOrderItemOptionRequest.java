package com.restaurant.system.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateOrderItemOptionRequest {

    @NotNull
    public Long option_id;

    public String option_type_snapshot;

    public String option_code_snapshot;

    public String option_group_snapshot;

    public Long parent_option_id_snapshot;

    public String option_name_snapshot_zh;

    public String option_name_snapshot_en;

    public BigDecimal option_price_snapshot;

    @NotNull
    @Min(1)
    public Integer quantity;
}
