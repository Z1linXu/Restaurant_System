package com.restaurant.system.menu.entity;

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
@Table(name = "menu_item_bom")
public class MenuItemBom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGSERIAL")
    public Long id;

    @Column(name = "menu_item_id")
    public Long menu_item_id;

    @Column(name = "inventory_item_id")
    public Long inventory_item_id;

    @Column(name = "qty_per_unit")
    public BigDecimal qty_per_unit;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
