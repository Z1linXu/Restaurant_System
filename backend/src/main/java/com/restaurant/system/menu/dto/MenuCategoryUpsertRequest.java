package com.restaurant.system.menu.dto;

public class MenuCategoryUpsertRequest {
    public Long store_id;
    public String name_zh;
    public String name_en;
    public Integer sort_order;
    public Boolean enabled;
    public Boolean is_active;
}
