package com.restaurant.system.menu.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MenuCatalogResponse {

    public Long store_id;
    public Long organization_id;
    public Long menu_revision;
    public LocalDateTime generated_at;
    public String catalog_version;
    public String combo_metadata_version;
    public String content_hash;
    public TaxPolicyResponse tax_policy;
    public List<CategoryResponse> categories;

    public MenuCatalogResponse(
        Long storeId,
        Long organizationId,
        Long menuRevision,
        LocalDateTime generatedAt,
        String catalogVersion,
        String comboMetadataVersion,
        TaxPolicyResponse taxPolicy,
        List<CategoryResponse> categories
    ) {
        this.store_id = storeId;
        this.organization_id = organizationId;
        this.menu_revision = menuRevision;
        this.generated_at = generatedAt;
        this.catalog_version = catalogVersion;
        this.combo_metadata_version = comboMetadataVersion;
        this.tax_policy = taxPolicy;
        this.categories = categories;
    }

    public static class TaxPolicyResponse {
        public BigDecimal rate;
        public String label;
        public String version;

        public TaxPolicyResponse(BigDecimal rate, String label, String version) {
            this.rate = rate;
            this.label = label;
            this.version = version;
        }
    }

    public static class CategoryResponse {
        public Long id;
        public String code;
        public String name_zh;
        public String name_en;
        public Integer sort_order;
        public Boolean is_active;
        public List<ItemResponse> items;

        public CategoryResponse(
            Long id,
            String code,
            String nameZh,
            String nameEn,
            Integer sortOrder,
            Boolean isActive,
            List<ItemResponse> items
        ) {
            this.id = id;
            this.code = code;
            this.name_zh = nameZh;
            this.name_en = nameEn;
            this.sort_order = sortOrder;
            this.is_active = isActive;
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
        public Boolean is_active;
        public Boolean is_sold_out;
        public Integer sort_order;
        public List<OptionResponse> options;

        public ItemResponse(
            Long id,
            Long categoryId,
            Long stationId,
            String nameZh,
            String nameEn,
            String sku,
            String itemType,
            BigDecimal basePrice,
            Boolean isActive,
            Boolean isSoldOut,
            Integer sortOrder,
            List<OptionResponse> options
        ) {
            this.id = id;
            this.category_id = categoryId;
            this.station_id = stationId;
            this.name_zh = nameZh;
            this.name_en = nameEn;
            this.sku = sku;
            this.item_type = itemType;
            this.base_price = basePrice;
            this.is_active = isActive;
            this.is_sold_out = isSoldOut;
            this.sort_order = sortOrder;
            this.options = options;
        }
    }

    public static class OptionResponse {
        public Long id;
        public String option_type;
        public String option_code;
        public String option_group;
        public Long parent_option_id;
        public Integer sort_order;
        public String name_zh;
        public String name_en;
        public BigDecimal price_delta;
        public Boolean is_active;
        public List<OptionResponse> side_item_remove_options;

        public OptionResponse(
            Long id,
            String optionType,
            String optionCode,
            String optionGroup,
            Long parentOptionId,
            Integer sortOrder,
            String nameZh,
            String nameEn,
            BigDecimal priceDelta,
            Boolean isActive
        ) {
            this.id = id;
            this.option_type = optionType;
            this.option_code = optionCode;
            this.option_group = optionGroup;
            this.parent_option_id = parentOptionId;
            this.sort_order = sortOrder;
            this.name_zh = nameZh;
            this.name_en = nameEn;
            this.price_delta = priceDelta;
            this.is_active = isActive;
            this.side_item_remove_options = List.of();
        }
    }
}
