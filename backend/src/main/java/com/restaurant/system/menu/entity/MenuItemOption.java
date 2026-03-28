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
@Table(name = "menu_item_options")
public class MenuItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGSERIAL")
    public Long id;

    @Column(name = "menu_item_id")
    public Long menu_item_id;

    @Column(name = "option_type")
    public String option_type;

    @Column(name = "name_zh")
    public String name_zh;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "price_delta")
    public BigDecimal price_delta;

    @Column(name = "is_active")
    public Boolean is_active;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
