package com.restaurant.system.menu.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MenuItemOptionAdminResponse {

    public Long id;
    public Long menu_item_id;
    public String option_type;
    public String option_code;
    public String option_group;
    public Long parent_option_id;
    public Integer sort_order;
    public String name_zh;
    public String name_en;
    public BigDecimal price_delta;
    public Boolean is_active;
    public LocalDateTime created_at;
    public LocalDateTime updated_at;
}
