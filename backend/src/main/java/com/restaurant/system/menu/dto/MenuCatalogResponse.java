package com.restaurant.system.menu.dto;

import java.math.BigDecimal;
import java.util.List;

public class MenuCatalogResponse {

    public Long store_id;
    public List<CategoryResponse> categories;

    public MenuCatalogResponse(Long store_id, List<CategoryResponse> categories) {
        this.store_id = store_id;
        this.categories = categories;
    }

    public static class CategoryResponse {
        public Long id;
        public String code;
        public String name_zh;
        public String name_en;
        public Integer sort_order;
        public List<ItemResponse> items;

        public CategoryResponse(
            Long id,
            String code,
            String name_zh,
            String name_en,
            Integer sort_order,
            List<ItemResponse> items
        ) {
            this.id = id;
            this.code = code;
            this.name_zh = name_zh;
            this.name_en = name_en;
            this.sort_order = sort_order;
            this.items = items;
        }
    }

    public static class ItemResponse {
        public Long id;
        public Long category_id;
        public Long station_id;
        public String name_zh;
        public String name_en;
        public String sku;
        public String item_type;
        public BigDecimal base_price;
        public Boolean is_sold_out;
        public List<OptionResponse> options;

        public ItemResponse(
            Long id,
            Long category_id,
            Long station_id,
            String name_zh,
            String name_en,
            String sku,
            String item_type,
            BigDecimal base_price,
            Boolean is_sold_out,
            List<OptionResponse> options
        ) {
            this.id = id;
            this.category_id = category_id;
            this.station_id = station_id;
            this.name_zh = name_zh;
            this.name_en = name_en;
            this.sku = sku;
            this.item_type = item_type;
            this.base_price = base_price;
            this.is_sold_out = is_sold_out;
            this.options = options;
        }
    }

    public static class OptionResponse {
        public Long id;
        public String option_type;
        public String name_zh;
        public String name_en;
        public BigDecimal price_delta;

        public OptionResponse(Long id, String option_type, String name_zh, String name_en, BigDecimal price_delta) {
            this.id = id;
            this.option_type = option_type;
            this.name_zh = name_zh;
            this.name_en = name_en;
            this.price_delta = price_delta;
        }
    }
}
