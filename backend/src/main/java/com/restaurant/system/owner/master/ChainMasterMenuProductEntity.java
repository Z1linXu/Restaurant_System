package com.restaurant.system.owner.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
    name = "chain_master_menu_products",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_chain_master_menu_products_key",
            columnNames = {"master_menu_version_id", "master_product_key"}
        ),
        @UniqueConstraint(
            name = "uq_chain_master_menu_products_ref",
            columnNames = {"master_menu_version_id", "item_ref"}
        )
    },
    indexes = {
        @Index(
            name = "idx_chain_master_menu_products_version",
            columnList = "master_menu_version_id,sort_order"
        ),
        @Index(
            name = "idx_chain_master_menu_products_sku",
            columnList = "master_menu_version_id,sku"
        )
    }
)
public class ChainMasterMenuProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "master_menu_version_id", nullable = false)
    public Long master_menu_version_id;

    @Column(name = "master_product_key", nullable = false)
    public String master_product_key;

    @Column(name = "item_ref", nullable = false)
    public String item_ref;

    @Column(name = "sku", nullable = false)
    public String sku;

    @Column(name = "master_category_key", nullable = false)
    public String master_category_key;

    @Column(name = "station_ref")
    public String station_ref;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "name_zh")
    public String name_zh;

    @Column(name = "item_type")
    public String item_type;

    @Column(name = "base_price", precision = 10, scale = 2)
    public BigDecimal base_price;

    @Column(name = "cost_per_item", precision = 10, scale = 2)
    public BigDecimal cost_per_item;

    @Column(name = "sort_order", nullable = false)
    public Integer sort_order = 0;

    @Column(name = "default_active", nullable = false)
    public Boolean default_active = true;

    @Column(name = "default_sold_out", nullable = false)
    public Boolean default_sold_out = false;

    @Column(name = "combo_allowed", nullable = false)
    public Boolean combo_allowed = false;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
