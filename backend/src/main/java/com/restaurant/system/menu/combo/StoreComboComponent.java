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
@Table(name = "store_combo_components")
public class StoreComboComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id", nullable = false)
    public Long store_id;

    @Column(name = "group_id", nullable = false)
    public Long group_id;

    @Column(name = "component_group", nullable = false)
    public String component_group;

    @Column(name = "component_code", nullable = false)
    public String component_code;

    @Column(name = "name_zh", nullable = false)
    public String name_zh;

    @Column(name = "name_en", nullable = false)
    public String name_en;

    @Column(name = "name_fr")
    public String name_fr;

    @Column(name = "enabled", nullable = false)
    public Boolean enabled;

    @Column(name = "display_order", nullable = false)
    public Integer display_order;

    @Column(name = "linked_menu_item_id")
    public Long linked_menu_item_id;

    @Column(name = "business_behavior", nullable = false)
    public String business_behavior;

    @Column(name = "archived_at")
    public LocalDateTime archived_at;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
