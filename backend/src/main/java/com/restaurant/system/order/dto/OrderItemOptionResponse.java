package com.restaurant.system.order.dto;

import java.math.BigDecimal;

public class OrderItemOptionResponse {

    public Long id;
    public Long option_id;
    public String option_type_snapshot;
    public String option_code_snapshot;
    public String option_group_snapshot;
    public Long parent_option_id_snapshot;
    public String option_name_snapshot_zh;
    public String option_name_snapshot_en;
    public BigDecimal price_delta;
    public Integer quantity;
}
