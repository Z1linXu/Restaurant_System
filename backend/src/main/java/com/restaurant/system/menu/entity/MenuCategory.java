package com.restaurant.system.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "menu_categories")
public class MenuCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGSERIAL")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "code")
    public String code;

    @Column(name = "name_zh")
    public String name_zh;

    @Column(name = "name_en")
    public String name_en;

    @Column(name = "sort_order")
    public Integer sort_order;

    @Column(name = "is_active")
    public Boolean is_active;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
