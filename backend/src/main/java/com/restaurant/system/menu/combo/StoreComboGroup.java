package com.restaurant.system.menu.combo;

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
@Table(name = "store_combo_groups")
public class StoreComboGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "group_code", nullable = false)
    public String group_code;

    @Column(name = "name_zh", nullable = false)
    public String name_zh;

    @Column(name = "name_en", nullable = false)
    public String name_en;

    @Column(name = "selection_rule", nullable = false)
    public String selection_rule;

    @Column(name = "required", nullable = false)
    public Boolean required;

    @Column(name = "enabled", nullable = false)
    public Boolean enabled;

    @Column(name = "display_order", nullable = false)
    public Integer display_order;

    @Column(name = "default_component_code")
    public String default_component_code;

    @Column(name = "archived_at")
    public LocalDateTime archived_at;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
