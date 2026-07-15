package com.restaurant.system.menu.dto;

import java.util.List;

public class MenuItemReorderRequest {
    public Long store_id;
    public List<Long> item_ids;
}
