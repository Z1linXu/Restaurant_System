package com.restaurant.system.menu.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "category_id")
    public Long category_id;

    @Column(name = "station_id")
    public Long station_id;

    @Column(name = "name_zh")
    public String name_zh;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "sku")
    public String sku;

    @Column(name = "item_type")
    public String item_type;

    @Column(name = "base_price")
    public BigDecimal base_price;

    @Column(name = "cost_per_item")
    @JsonProperty("cost_per_item")
    public BigDecimal cost_per_item;

    @Column(name = "is_active")
    public Boolean is_active;

    @Column(name = "is_sold_out")
    public Boolean is_sold_out;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
