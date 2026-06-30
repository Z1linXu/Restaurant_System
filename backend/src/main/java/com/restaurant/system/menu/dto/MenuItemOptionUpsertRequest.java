package com.restaurant.system.menu.dto;

import java.math.BigDecimal;

public class MenuItemOptionUpsertRequest {

    public String option_type;
    public String option_code;
    public String option_group;
    public Long parent_option_id;
    public Integer sort_order;
    public String name_zh;
    public String name_en;
    public BigDecimal price_delta;
    public Boolean is_active;
}
