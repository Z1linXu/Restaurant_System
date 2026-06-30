package com.restaurant.system.menu.dto;

import java.util.List;

public class MenuItemOptionReorderRequest {

    public List<OptionOrder> options;

    public static class OptionOrder {
        public Long id;
        public Integer sort_order;
    }
}
