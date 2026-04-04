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
@Table(name = "prep_recipe_details")
public class PrepRecipeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "prep_recipe_id")
    private Long prep_recipe_id;

    @Column(name = "input_inventory_item_id")
    private Long input_inventory_item_id;

    @Column(name = "input_qty")
    private BigDecimal input_qty;

    @Column(name = "input_unit")
    private String input_unit;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;
}
