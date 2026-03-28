package com.restaurant.system.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGSERIAL")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "name")
    public String name;

    @Column(name = "code")
    public String code;

    @Column(name = "item_level")
    public String item_level;

    @Column(name = "item_type")
    public String item_type;

    @Column(name = "unit")
    public String unit;

    @Column(name = "current_stock")
    public BigDecimal current_stock;

    @Column(name = "safety_stock")
    public BigDecimal safety_stock;

    @Column(name = "default_prep_batch")
    public BigDecimal default_prep_batch;

    @Column(name = "is_key_item")
    public Boolean is_key_item;

    @Column(name = "is_active")
    public Boolean is_active;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
