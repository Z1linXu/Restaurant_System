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

    @Column(name = "component_group", nullable = false)
    public String component_group;

    @Column(name = "component_code", nullable = false)
    public String component_code;

    @Column(name = "name_zh", nullable = false)
    public String name_zh;

    @Column(name = "name_en", nullable = false)
    public String name_en;

    @Column(name = "enabled", nullable = false)
    public Boolean enabled;

    @Column(name = "display_order", nullable = false)
    public Integer display_order;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime created_at;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updated_at;
}
